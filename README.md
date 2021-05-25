<p align="center"><a href="https://tonikelope.github.io/coronapoker/" target="_blank">https://tonikelope.github.io/coronapoker/<br><img src="https://raw.githubusercontent.com/tonikelope/coronapoker/gh-pages/screenshots/1.png"><br><img src="https://raw.githubusercontent.com/tonikelope/coronapoker/gh-pages/screenshots/13.png"></a></p>
<p align="center"><a href="https://adoptopenjdk.net/" target="_blank">JAVA</a> 11 or above is required.</p>


### BUILDING CORONAPOKER FROM SOURCE:

#### Step 1 (install coronahmac.jar as Maven local package):
```
mvn install:install-file -Dfile=coronaHMAC_x.x.x.jar -DgroupId=com.tonikelope.coronahmac -DartifactId=coronahmac -Dversion=x.x.x -Dpackaging=jar
```

#### Step 2 (build):
```
mvn clean install
```

#### EXTRA: building WITHOUT coronahmac dependency:

##### Step 1 (edit pom.xml):
###### 1.1 Change MAIN CLASS:
```
<mainClass>com.tonikelope.coronahmac.M</mainClass>
```
to:

```
<mainClass>com.tonikelope.coronapoker.Init</mainClass>
```

###### 1.2 Delete coronahmac dependency:
```
<dependency>
   <groupId>com.tonikelope.coronahmac</groupId>
   <artifactId>coronahmac</artifactId>
   <version>1.1.24</version>
</dependency>
```

##### Step 2 (build):
```
mvn clean install
```

### NOTE: What is coronahmac.jar?

It is a small module that is loaded at game startup to mitigate any player (including the server) trying to cheat using a hacked version of the game. For obvious reasons the source code is not available (if for any reason you are not comfortable using closed source dependencies, you can build coronapoker without this module).

