echo "Set java to 25"
jenv local 25
export JAVA_HOME="$(jenv javahome)"
echo "build survey"

./mvnw clean package -Dquarkus.profile=docker || exit $?

# An image carries no Vaadin development tooling: the vaadin-dev-tools profile in pom.xml is
# keyed on -Dquarkus.profile being absent, so the command above leaves vaadin-dev out. Check what
# was packaged rather than trust the flag, and fail the build if the libraries say otherwise (C-007).
LIB_DIR="${LIB_DIR:-target/quarkus-app/lib/main}"
if ls "${LIB_DIR}" 2>/dev/null | grep -qi '^com\.vaadin\.vaadin-dev'; then
    echo "Aborting: vaadin-dev is among the packaged libraries in ${LIB_DIR}; the image was built without -Dquarkus.profile=docker." >&2
    exit 1
fi
