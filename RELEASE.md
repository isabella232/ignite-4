# Apache Ignite 3 Release Procedure

This document describes the current procedure for preparing an Ignite 3 release.

## Prerequisites

1. Create a GPG key, upload it to a keyserver, and locate its ID. More details here: https://infra.apache.org/openpgp.html
2. Checkout Apache distribution directories:
   ```
   svn checkout https://dist.apache.org/repos/dist/dev/ignite dist-dev
   svn checkout https://dist.apache.org/repos/dist/release/ignite dist-release
   ```
3. Provide your Apache credentials to Maven (required for uploading to Apache Nexus):
   ```xml
   <settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
             xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd">
     <servers>
       <server>
         <id>apache.staging</id>
         <username>USERNAME</username>
         <password>PASSWORD</password>
       </server>
     </servers>
   </settings>
   ```

For all the commands going forward:
* Replace `{version}` with the version number being released.
* Replace `{rc}` with the current sequential number of the release candidate.
* Replace `{gpg}` with your GPG key ID.
* Replace `{dist.dev}` with the local path to the development distribution directory.
* Replace `{dist.release}` with the local path to the release distribution directory.

## Preparing the Release

1. Go to the project home folder.
2. Create a Git tag:
   ```
   git tag -a {version}-rc{rc} -m "{version}-rc{rc}"
   git push --tags
   ```
3. Build the project, sign the artifact and create a staging repository:
   ```
   mvn clean verify gpg:sign deploy:deploy -Dgpg.keyname={gpg} [-DskipTests]
   ```
4. Login to the Apache Nexus and close the new repository: https://repository.apache.org/#stagingRepositories
5. Create an empty folder under the development distribution directory:
   ```
   rm -rf {dist.dev}/{version}-rc{rc}
   mkdir {dist.dev}/{version}-rc{rc}
   ```
6. Create a source code package:
   ```
   git archive --prefix=apache-ignite-{version}-src/ -o target/apache-ignite-{version}-src.zip HEAD
   ```
7. Switch to the `target` folder:
   ```
   cd target
   ```
8. Create checksums and sign ZIP packages:
   ```
   gpg -a -u {gpg} -b apache-ignite-{version}-src.zip
   gpg -a -u {gpg} -b apache-ignite-{version}.zip
   gpg --print-md SHA512 apache-ignite-{version}-src.zip > apache-ignite-{version}-src.zip.sha512
   gpg --print-md SHA512 apache-ignite-{version}.zip > apache-ignite-{version}.zip.sha512
   ```
9. Copy ZIP packages along with checksums and signatures to the development distribution directory:
   ```
   cp apache-ignite-{version}-src.zip apache-ignite-{version}-src.zip.asc apache-ignite-{version}-src.zip.sha512 \
      apache-ignite-{version}.zip apache-ignite-{version}.zip.asc apache-ignite-{version}.zip.sha512  \
      /Users/vkulichenko/GridGain/dist-dev/{version}-rc{rc}
   ```
10. Commit ZIP packages:
   ```
   cd {dist.dev}
   svn add {version}-rc{rc}
   svn commit -m “Apache Ignite {version} RC{rc}”
   ```
11. Put the release on a vote on the developers mailing list.

## Finalizing the Release

Perform the following actions ONLY after the vote is successful and closed.

1. Login to the Apache Nexus and release the staging repository: https://repository.apache.org/#stagingRepositories
2. Create an empty folder under the release distribution directory:
   ```
   rm -rf {dist.release}/{version}
   mkdir {dist.release}/{version}
   ```
3. Copy ZIP packages along with checksums and signatures to the release distribution directory:
   ```
   cp {dist.dev}/{version}-rc{rc}/* {dist.release}/{version}
   ```
4. Commit ZIP packages:
   ```
   cd {dist.release}
   svn add {version}
   svn commit -m “Apache Ignite {version}”
   ```
