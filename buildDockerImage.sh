echo "Set java to 21"
jenv local 21
echo "build survey"

./mvnw clean package -Dmaven.test.skip=true -Dquarkus.profile=docker -Pproduction