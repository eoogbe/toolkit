# Copyright 2019 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

load("@bazel_tools//tools/build_defs/pkg:pkg.bzl", "pkg_tar")
load("//rules_gapic:gapic_pkg.bzl", "construct_package_dir_paths")

def _py_gapic_build_configs_pkg_impl(ctx):
    paths = construct_package_dir_paths(
        ctx.attr.package_dir,
        ctx.outputs.pkg,
        ctx.label.name,
    )

    substitutions = dict(ctx.attr.static_substitutions)
    substitutions["{{dependencies}}"] = _construct_build_deps_subs(ctx.attr.deps)

    expanded_templates = []
    for template in ctx.attr.templates.items():
        expanded_template = ctx.actions.declare_file(
            "%s/%s" % (paths.package_dir_sibling_basename, template[1]),
            sibling = paths.package_dir_sibling_parent,
        )
        expanded_templates.append(expanded_template)
        ctx.actions.expand_template(
            template = template[0].files.to_list()[0],
            output = expanded_template,
            substitutions = substitutions,
        )

    script = """
    mkdir -p {package_dir_path}
    for templ in {templates}; do
        cp ${{templ}} {package_dir_path}/
    done
    cd {package_dir_path}
    tar -zchpf {package_dir}.tar.gz {package_dir_expr}
    cd -
    mv {package_dir_path}/{package_dir}.tar.gz {pkg}
    rm -rf {package_dir_path}
    """.format(
        templates = " ".join(["'%s'" % f.path for f in expanded_templates]),
        package_dir_path = paths.package_dir_path,
        package_dir = paths.package_dir,
        pkg = ctx.outputs.pkg.path,
        package_dir_expr = paths.package_dir_expr,
    )

    ctx.actions.run_shell(
        inputs = expanded_templates,
        command = script,
        outputs = [ctx.outputs.pkg],
    )

py_gapic_build_configs_pkg = rule(
    implementation = _py_gapic_build_configs_pkg_impl,
    attrs = {
        "deps": attr.label_list(
            doc = "The dependencies of the main source.",
            mandatory = True,
            allow_empty = False,
        ),
        "templates": attr.label_keyed_string_dict(
            doc = "A mapping from template files to their names.",
            mandatory = True,
            allow_files = True,
            allow_empty = False,
        ),
        "static_substitutions": attr.string_dict(
            doc = "Static substitutions to make when expanding the templates.",
            mandatory = False,
            default = {},
        ),
        "package_dir": attr.string(
            doc = "The directory containing the assembly package.",
            mandatory = False,
            default = "",
        ),
    },
    outputs = {"pkg": "%{name}.tar.gz"},
    doc = """Generates the build-specific resources, such as setup.py, from
    templates.
    """,
)

def _construct_build_deps_subs(deps):
    """Constructs the build dependencies listed in setup.py."""
    result = []
    for dep in deps:
        if _is_gapic_pkg_dependency(dep):
            dep_file = dep.files.to_list()[0]
            dep_name = dep_file.basename
            for ext in (".tar.gz", ".gz", ".tgz"):
                if dep_name.endswith(ext):
                    dep_name = dep_name[:-len(ext)]
                    break
            result.append("%s," % dep_name)
    result.append("'enum34;python_version<\"3.4\"',")

    return "\n  ".join(result)

def _is_gapic_pkg_dependency(dep):
    """Returns true if the dependency is a GAPIC package."""
    files_list = dep.files.to_list()
    if len(files_list) != 1:
        return False

    return files_list[0].extension in ["gz", "tgz"]

def _py_gapic_src_pkg_impl(ctx):
    gapic_srcs = []
    for dep in ctx.attr.deps:
        gapic_srcs.extend(dep.files.to_list())

    test_srcs = []
    for dep in ctx.attr.test_deps:
        for dep_file in dep.files.to_list():
            if dep_file.extension == "srcjar":
                test_srcs.append(dep_file)

    paths = construct_package_dir_paths(
        ctx.attr.package_dir,
        ctx.outputs.pkg,
        ctx.label.name,
    )

    script = """
    mkdir -p {package_dir_path}
    for gapic_src in {gapic_srcs}; do
        if [ -d "${{gapic_src}}" ]; then
            cp -R -L ${{gapic_src}}/* {package_dir_path}/
        fi
    done
    for test_src in {test_srcs}; do
        mkdir -p {package_dir_path}/tests
        unzip -q -o ${{test_src}} -d {package_dir_path}/tests
    done
    cd {package_dir_path}
    tar -zchpf {package_dir}.tar.gz {package_dir_expr}
    cd -
    mv {package_dir_path}/{package_dir}.tar.gz {pkg}
    rm -rf {package_dir_path}
    """.format(
        gapic_srcs = " ".join(["'%s'" % f.path for f in gapic_srcs]),
        test_srcs = " ".join(["'%s'" % f.path for f in test_srcs]),
        package_dir_path = paths.package_dir_path,
        package_dir = paths.package_dir,
        pkg = ctx.outputs.pkg.path,
        package_dir_expr = paths.package_dir_expr,
    )

    ctx.actions.run_shell(
        inputs = gapic_srcs,
        command = script,
        outputs = [ctx.outputs.pkg],
    )

_py_gapic_src_pkg = rule(
    implementation = _py_gapic_src_pkg_impl,
    attrs = {
        "deps": attr.label_list(
            doc = """The dependencies of the main source, including the Python
            proto stubs.
            """,
            mandatory = True,
            allow_files = True,
            allow_empty = False,
        ),
        "test_deps": attr.label_list(
            doc = "The dependencies of the test source.",
            mandatory = False,
            allow_files = True,
        ),
        "package_dir": attr.string(
            doc = "The directory containing the assembly package.",
            mandatory = False,
            default = "",
        ),
    },
    outputs = {"pkg": "%{name}.tar.gz"},
    doc = """Assembles the GAPIC client Python source files into an idiomatic
    Python package.
    """,
)

def py_gapic_assembly_pkg(
        name,
        deps,
        test_deps = [],
        proto_deps = [],
        version = "",
        **kwargs):
    """Packages the previously built GAPIC client artifacts into an idiomatic
    Python package, ready for opensourcing independently from Bazel.
    """
    resource_target_name = "%s-resources" % name
    setup_tmpl_label = Label("//rules_gapic/python:resources/setup.py.tmpl")
    py_gapic_build_configs_pkg(
        name = resource_target_name,
        deps = deps,
        templates = {
            setup_tmpl_label: "setup.py",
        },
        static_substitutions = {
            "{{name}}": name,
            "{{version}}": version,
        },
        **kwargs
    )

    srcs_pkg_target_name = "%s-srcs_pkg" % name
    _py_gapic_src_pkg(
        name = srcs_pkg_target_name,
        deps = proto_deps + deps,
        test_deps = test_deps,
        **kwargs
    )

    pkg_tar(
        name = name,
        extension = "tar.gz",
        deps = [
            Label("//rules_gapic/python:manifest"),
            resource_target_name,
            srcs_pkg_target_name,
        ],
        package_dir = name,
        **kwargs
    )
