# verificare 2026-11-13T22:02:58Z

## server: d496911d

### stare/resurse
{"object":"stats","attributes":{"current_state":"running","is_suspended":false,"resources":{"memory_bytes":600121344,"cpu_absolute":11.449,"disk_bytes":460831131,"network_rx_bytes":1028901,"network_tx_bytes":6993333,"uptime":234073}}}

### startup
{"object":"list","data":[{"object":"egg_variable","attributes":{"name":"Server Jar File","description":"The name of the server jarfile to run the server with.","env_variable":"SERVER_JARFILE","default_value":"server.jar","server_value":"neoforge-21.1.172-installer.jar","is_editable":true,"rules":"required|regex:\/^([\\w\\d._-]+)(\\.jar)$\/"}},{"object":"egg_variable","attributes":{"name":"JVM Startup Flags \/ Arguments","description":"The flags variable is used to customize Java startup arguments to optimize the performance of the Minecraft server. These arguments affect critical aspects like memory allocation, garbage collection, and overall server performance tuning. Caution: Modifying flags can significantly impact server performance. For more information please check: https:\/\/docs.papermc.io\/paper\/aikars-flags and https:\/\/flags.sh\/","env_variable":"JVM_STARTUP_FLAGS","default_value":"-XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 -XX:InitiatingHeapOccupancyPercent=15 -XX:G1MixedGCLiveThresholdPercent=90 -XX:G1RSetUpdatingPauseTimePercent=5 -XX:SurvivorRatio=32 -XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1 -Dusing.aikars.flags=https:\/\/mcflags.emc.gs -Daikars.new.flags=true -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M -XX:G1ReservePercent=20","server_value":"-XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 -XX:InitiatingHeapOccupancyPercent=15 -XX:G1MixedGCLiveThresholdPercent=90 -XX:G1RSetUpdatingPauseTimePercent=5 -XX:SurvivorRatio=32 -XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1 -Dusing.aikars.flags=https:\/\/mcflags.emc.gs -Daikars.new.flags=true -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M -XX:G1ReservePercent=20","is_editable":true,"rules":"nullable|string"}},{"object":"egg_variable","attributes":{"name":"Forge Install Server","description":"If you have the server installer and it gives you any errors, set this variable for server installation only to: \"--installServer\". For more information please check: https:\/\/docs.hidencloud.com\/minecraft\/change-minecraft-type-and-version#paper-and-forge-installation-video","env_variable":"INSTALL_SERVER","default_value":"","server_value":"","is_editable":true,"rules":"nullabl

### fisiere in /
dir           4096 .horizon
dir           4096 .horizon-cache
dir           4096 26.3
dir           4096 config
dir           4096 horizon
dir           4096 logs
dir           4096 resources
dir           4096 world
file      13323406 26.3.tar.gz
file             2 banned-ips.json
file             2 banned-players.json
file            10 eula.txt
file          4623 neoforge-21.1.172-installer.jar
file          3607 neoforge-21.1.172-installer.jar.bak2
file             2 ops.json
file          1865 server.properties
file            72 server.properties.imported
file          6483 server.toml
file           107 usercache.json
file             2 whitelist.json


### resources/
{"object":"list","data":[{"object":"file_object","attributes":{"name":"assets","mode":"drwxr-xr-x","mode_bits":"755","size":4096,"is_file":false,"is_symlink":false,"mimetype":"inode\/directory","created_at":"2026-11-13T21:59:01+00:00","modified_at":"2026-11-13T21:59:01+00:00"}},{"object":"file_object","attributes":{"name":"biome-parameters","mode":"drwxr-xr-x","mode_bits":"755","size":4096,"is_file":false,"is_symlink":false,"mimetype":"inode\/directory","created_at":"2026-11-13T21:59:01+00:00","modified_at":"2026-11-13T21:59:01+00:00"}},{"object":"file_object","attributes":{"name":"classes","mode":"drwxr-xr-x","mode_bits":"755","size":4096,"is_file":false,"is_symlink":false,"mimetype":"inode\/directory","created_at":"2026-11-13T21:59:01+00:00","modified_at":"2026-11-13T21:59:01+00:00"}},{"object":"file_object","attributes":{"name":"client-classes","mode":"drwxr-xr-x","mode_bits":"755","size":4096,"is_file":false,"is_symlink":false,"mimetype":"inode\/directory","created_at":"2026-11-13T21:59:01+00:00","modified_at":"2026-11-13T21:59:01+00:00"}},{"object":"file_object","attributes":{"name":"data","mode":"drwxr-xr-x","mode_bits":"755","size":4096,"is_file":false,"is_symlink":false,"mi

### server.toml
# Horizon server settings (docs/server.md). The server writes them into server.properties before
# every start: edit this file, not that one.

[server]
motd = "A Minecraft Server"  # the line under the server's name in the server list
port = 24601
bind = "0.0.0.0"                    # listen on every address, or only on this one (server-ip)
max_players = 20             # how many players can be online at once
online_mode = true           # check players' Minecraft accounts (false: anyone joins under any name)
whitelist = false
enforce_whitelist = false    # kick online players taken off the whitelist
view_distance = 10           # chunks sent around each player
simulation_distance = 10     # chunks around each player where mobs, crops and redstone tick
max_view_distance = 0        # past view_distance, saved chunks only, up to this (never generated; 0: off)
admin_feedback_to_ops = true # operators see each other's commands ([name: ...], vanilla); false: only the log
accept_older_clients = false # true: 26.2 clients join too, their packets translated (docs/hosting.md)

[world]
name = "world"               # the world folder
seed = ""                    # empty: a random seed on the first start
game_mode = "survival"       # survival, creative, adventure or spectator
difficulty = "easy"          # peaceful, easy, normal or hard
hardcore = false             # one life: a player who dies becomes a spectator

# Any other server.properties key, spelled as vanilla spells it, for example:
# spawn-protection = 16
[properties]
"query.enabled" = true
"query.port" = 24601

[engine]
path = ""                    # the game program; empty: the one installed beside horizon-server
resources = ""               # the prepared game files; empty: resources/ here, else the usual places
resta

### ping Minecraft 26.3
