the files contain the DBpedia query log by 05/08/2012

dbpedia.log.gz - Raw log
dbpedia.log-valid.gz - only valid SPARQL queries
dbpedia.log-valid+select.gz - only valid SPARQL SELECT queries
dbpedia.log-valid+select+nonempty.gz - only valid non-empty SPARQL SELECT queries
dbpedia.log-valid+select+nonempty+ip.gz - only valid non-empty SPARQL SELECT queries where the number of queries grouped by IP address is lower than SQRT(n)