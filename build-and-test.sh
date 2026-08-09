rm "/home/dragon/.local/share/PrismLauncher/instances/Main 1.8/minecraft/mods/Myaulex-V1.jar"
rm build/libs/Myaulex-V1.jar
./gradlew build
cp /home/dragon/IdeaProjects/Myaulex/build/libs/Myaulex-V1.jar "/home/dragon/.local/share/PrismLauncher/instances/Main 1.8/minecraft/mods"
"/usr/bin/prismlauncher" '--launch' 'Main 1.8'
echo "Success!"
