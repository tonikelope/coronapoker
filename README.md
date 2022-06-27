This is the project of a perfectionist who one day wanted to develop the best open source poker game. With all my love and dedication, for you. I hope you enjoy playing it as much as I enjoy programming it. Carpe diem.

<h1 align="center"><a href="https://www.youtube.com/watch?v=lAUBjjssKGk"><b>GAMEPLAY VIDEO</b></a></h1>
<p align="center"><img src="https://raw.githubusercontent.com/tonikelope/coronapoker/gh-pages/screenshots/1.png"><br><img src="https://raw.githubusercontent.com/tonikelope/coronapoker/gh-pages/screenshots/13.png"></a></p>

CoronaPoker was built using (the old, but not obsolete) https://en.wikipedia.org/wiki/Swing_(Java)

### BUILDING CORONAPOKER FROM SOURCE:

#### Step 1 (install coronahmac.jar as Maven local package):
```
mvn install:install-file -Dfile=coronaHMAC_x.x.x.jar -DgroupId=com.tonikelope.coronahmac -DartifactId=coronahmac -Dversion=x.x.x -Dpackaging=jar
```

#### Step 2 (build):
```
mvn clean install
```

#### What is CoronaHMAC?

It is a small module that is loaded at game startup to mitigate any player (including the server) trying to cheat using a hacked version of the game. For obvious reasons the source code is not available (if for any reason you are not comfortable using closed source dependencies, you can build coronapoker without this module).

<i>Advice: if you plan to distribute CoronaPoker as a package for your favorite Linux distribution, it is HIGHLY RECOMMENDED that you download from here the compiled jar or build it from the source code but without including the CoronaHMAC dependency (otherwise, every user built binary would be different generating false positives in the CoronaHMAC anti-cheating module).</i>

##### BUILDING WITHOUT coronahmac dependency:

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



