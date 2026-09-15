echo "Set java to 25"
jenv local 25
export JAVA_HOME="$(jenv javahome)"
echo "build survey"

./mvnw clean package -Dmaven.test.skip=true -Dquarkus.profile=docker