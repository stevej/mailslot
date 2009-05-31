java -agentlib:hprof=heap=sites,cpu=samples,depth=10,monitor=y,thread=y -server -Xms512m -Xmx512m -jar mailslot-1.0.jar -f config/test.conf
