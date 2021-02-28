<p align="center"><a href="https://tonikelope.github.io/coronapoker/" target="_blank">https://tonikelope.github.io/coronapoker/<br><img src="https://raw.githubusercontent.com/tonikelope/coronapoker/gh-pages/screenshots/corona.png"><br><img src="https://raw.githubusercontent.com/tonikelope/coronapoker/gh-pages/screenshots/corona2.png"></a></p>
<p align="center"><a href="https://adoptopenjdk.net/" target="_blank">JAVA</a> 11 or above is required.</p>


### What is coronahmac.jar?

It is a small module that is loaded at startup to mitigate any player (including the server) trying to cheat using a hacked version of the game. For obvious reasons the source code is not available.

#### Installing coronaHMAC as Maven local package:
```
mvn install:install-file -Dfile=coronaHMAC_x.x.x.jar -DgroupId=com.tonikelope.coronahmac -DartifactId=coronahmac -Dversion=x.x.x -Dpackaging=jar
```
