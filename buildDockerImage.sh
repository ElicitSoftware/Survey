echo "Set java to 21"
jenv local 21.0.2
echo "build survey"

./mvnw clean package -Dmaven.test.skip=true -Dquarkus.profile=docker -Pproduction
# ./mvnw clean package -Dmaven.test.skip=true -Dnative -Dquarkus.native.container-build=true -Dquarkus.container-image.build=true -Dquarkus.profile=docker