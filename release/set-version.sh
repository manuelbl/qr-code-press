#!/usr/bin/env bash
#
# Rewrites the version strings of the QR Code Press repository.
#
# A released repository documents one version and builds another: `main` carries a snapshot in
# every pom, while its README documents the version that is on Maven Central and links to the
# examples of the tag that produced it. Hence two modes:
#
#   set-version.sh 0.9.0                        poms and README   (the commit that gets tagged)
#   set-version.sh --poms-only 0.10.0-SNAPSHOT  poms only         (the commit after the release)
#
# The rewrite is pure text: it touches no git state, and running it twice with the same version
# changes nothing. That is what lets the release workflow reproduce the same tree in several jobs
# from a plain checkout instead of passing a working tree between them.
#
# Poms are discovered, not listed, so an example added under examples/ is picked up on its own.
# `.claude/skills/adding-an-example/SKILL.md` is deliberately left alone: the version in it is a
# template for contributors working on `main` and must stay a snapshot.
#
# Only POSIX tools are used, so the script runs the same on a developer machine and on a runner.

set -euo pipefail

readonly REPO_URL="https://github.com/manuelbl/qr-code-press"

usage() {
    cat >&2 <<'USAGE'
usage: set-version.sh [--poms-only] <version>

  <version>      x.y.z, or x.y.z-SNAPSHOT with --poms-only

  --poms-only    rewrite the poms but leave README.md alone, so the documented
                 release survives the bump back to a snapshot
USAGE
}

fail() {
    echo "set-version.sh: $*" >&2
    exit 1
}

poms_only=false
if [[ "${1:-}" == "--poms-only" ]]; then
    poms_only=true
    shift
fi

version="${1:-}"
if [[ $# -ne 1 || -z "$version" ]]; then
    usage
    exit 2
fi

if [[ ! "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-SNAPSHOT)?$ ]]; then
    echo "set-version.sh: '$version' is not a version of the form x.y.z or x.y.z-SNAPSHOT" >&2
    exit 2
fi

# The README documents what a user puts in their pom, so it can only ever name a real release.
if [[ "$poms_only" == false && "$version" == *-SNAPSHOT ]]; then
    echo "set-version.sh: refusing to document '$version' in README.md; use --poms-only" >&2
    exit 2
fi

cd "$(dirname "$0")/.."

# Runs a filter over a file in place. `sed -i` is not portable, BSD and GNU disagree about it, so
# the filter reads the file and its output replaces it.
apply() {
    local file="$1"
    shift
    if "$@" < "$file" > "$file.tmp"; then
        mv "$file.tmp" "$file"
    else
        rm -f "$file.tmp"
        return 1
    fi
}

# The project version is the first <version> element of a pom, and the dependency snippet is the
# first one in the README; every later one belongs to a plugin or a dependency. <modelVersion>
# does not match, the tag name differs. This is awk rather than sed because replacing only the
# first match is not portable in sed.
set_first_version() {
    awk -v v="$1" '
        !replaced && sub(/<version>[^<]*<\/version>/, "<version>" v "</version>") { replaced = 1 }
        { print }
    '
}

# The property by which every example and the profiling harness resolve the library.
set_library_property() {
    awk -v v="$1" '
        {
            gsub(/<qr-code-press\.version>[^<]*<\/qr-code-press\.version>/,
                 "<qr-code-press.version>" v "</qr-code-press.version>")
            print
        }
    '
}

# Pins the README links to the examples at the release tag, so that following one lands on
# examples that resolve that release from Maven Central. Any existing link target is matched,
# pinned or not, which is what makes the rewrite idempotent.
pin_example_links() {
    awk -v v="$1" -v repo="$2" -v names="$3" '
        BEGIN { count = split(names, name, " ") }
        {
            for (i = 1; i <= count; i++)
                gsub("\\]\\([^)]*examples/" name[i] "/?\\)",
                     "](" repo "/tree/v" v "/examples/" name[i] "/)")
            print
        }
    '
}

# Reads back what the rewrite should have written. Every pattern above is silent when it matches
# nothing, so a pom that gets reformatted, or a README link that gets reworded, would otherwise
# leave a version unchanged and still exit zero. These read-backs turn that into a failed release.
first_version_of() {
    awk -F'</?version>' '/<version>/ { print $2; exit }' "$1"
}

library_property_of() {
    awk -F'</?qr-code-press.version>' '/<qr-code-press.version>/ { print $2; exit }' "$1"
}

for pom in qr-code-press/pom.xml profiling/pom.xml examples/*/pom.xml; do
    apply "$pom" set_first_version "$version"
    apply "$pom" set_library_property "$version"

    actual="$(first_version_of "$pom")"
    [[ "$actual" == "$version" ]] \
        || fail "$pom has project version '$actual' after the rewrite, expected '$version'"

    # The library pom has no such property, the examples and the profiling harness do. Keyed on
    # the tag being present rather than on the value being readable, so that a property the
    # rewrite failed to reach reports an empty value instead of being skipped.
    if grep -Fq '<qr-code-press.version>' "$pom"; then
        property="$(library_property_of "$pom")"
        [[ "$property" == "$version" ]] \
            || fail "$pom has qr-code-press.version '$property' after the rewrite, expected '$version'"
    fi

    echo "set $version in $pom"
done

if [[ "$poms_only" == true ]]; then
    exit 0
fi

example_names=""
for dir in examples/*/; do
    example_names="$example_names $(basename "$dir")"
done

apply README.md set_first_version "$version"
apply README.md pin_example_links "$version" "$REPO_URL" "$example_names"

actual="$(first_version_of README.md)"
[[ "$actual" == "$version" ]] \
    || fail "README.md documents version '$actual' after the rewrite, expected '$version'"

for name in $example_names; do
    grep -Fq "]($REPO_URL/tree/v$version/examples/$name/)" README.md \
        || fail "README.md has no link to example '$name' pinned to v$version"
done

# Catches a link to a directory under examples/ that carries no pom, and so was never pinned.
if grep -Eq '\]\(examples/' README.md; then
    fail "README.md still has an unpinned example link"
fi

echo "set $version in README.md, with example links pinned to v$version"
