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

def _py_gapic_src_pkg_impl(ctx):
    proto_grpc_srcs = []
    gapic_srcs = []
    srcjars = []
    for dep in ctx.attr.deps:
        if ProtoInfo in dep:
            proto_grpc_srcs.extend(dep[ProtoInfo].check_deps_sources)
        elif PyInfo in dep:
            gapic_srcs.extend(dep[PyInfo].transitive_sources.to_list())
        elif hasattr(dep, "files"):
            for dep_file in dep.files.to_list():
                if dep_file.extension == "srcjar":
                    srcjars.append(dep_file)

    paths = construct_package_dir_paths(
        ctx.attr.package_dir,
        ctx.outputs.pkg,
        ctx.label.name,
    )

    script = """
    mkdir -p {package_dir_path}
    for proto_grpc_src in {proto_grpc_srcs}; do
        unzip -q -o $proto_grpc_src -d {package_dir_path}
    done
    for srcjar in {srcjars}; do
        unzip -q -o $srcjar -d {package_dir_path}
    done
    for gapic_src in {gapic_srcs}; do
        if [ -d "${{gapic_src}}" ]; then
            cp -R -L ${{gapic_src}}/* {package_dir_path}/
        fi
    done
    cd {package_dir_path}
    tar -zchpf {package_dir}.tar.gz {package_dir_expr}
    cd -
    mv {package_dir_path}/{package_dir}.tar.gz {pkg}
    rm -rf {package_dir_path}
    """.format(
        proto_grpc_srcs = " ".join(["'%s'" % f.path for f in proto_grpc_srcs]),
        srcjars = " ".join(["'%s'" % f.path for f in srcjars]),
        gapic_srcs = " ".join(["'%s'" % f.path for f in gapic_srcs]),
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
            doc = """The dependencies of the sources files, including the Python
            proto stubs.
            """,
            mandatory = True,
            allow_files = True,
            allow_empty = False,
        ),
        "package_dir": attr.string(
            doc = "The directory containing the assembly package.",
            mandatory = True,
        ),
    },
    outputs = {"pkg": "%{name}.tar.gz"},
    doc = """Assembles the GAPIC client Python source files into an idiomatic
    Python package.
    """,
)

def py_gapic_assembly_pkg(name, deps, version = "", **kwargs):
    """Packages the previously built GAPIC client artifacts into an idiomatic
    Python package, ready for opensourcing independently from Bazel.
    """
    _py_gapic_src_pkg(
        name = name,
        deps = deps,
        package_dir = name,
        **kwargs
    )
