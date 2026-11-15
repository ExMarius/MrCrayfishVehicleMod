# mod .hmod pe panel

Connected to jacob.hidencloud.com.
sftp> -mkdir horizon/horizon-mods
sftp> -rm horizon/horizon-mods/vehicle.hmod
Removing /horizon/horizon-mods/vehicle.hmod
sftp> put out/vehicle.hmod horizon/horizon-mods/vehicle.hmod
Uploading out/vehicle.hmod to /horizon/horizon-mods/vehicle.hmod
sftp> put out/horizon-mods.txt horizon/horizon-mods.txt
Uploading out/horizon-mods.txt to /horizon/horizon-mods.txt
sftp> ls -l horizon/horizon-mods/vehicle.hmod
-rw-r--r--    ? 997      986          7279 Nov 15 16:51 horizon/horizon-mods/vehicle.hmod
sftp> ls -l horizon/horizon-mods.txt
-rw-r--r--    ? 997      986            17 Nov 15 16:51 horizon/horizon-mods.txt
