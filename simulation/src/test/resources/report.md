# Report
<!-- Run SimulationTest to regenerate this report. -->
```
                all_nodes_500[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=79.3%	client_mean=PT6.15542S     	server_cpu=PT20M          	client_received=2000/2000	server_resps=2000	codes={200=1586, 500=414}
                    all_nodes_500[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=73.7%	client_mean=PT3.27468S     	server_cpu=PT20M          	client_received=2000/2000	server_resps=2000	codes={200=1474, 500=526}
                              all_nodes_500[UNLIMITED_ROUND_ROBIN].txt:	success=50.0%	client_mean=PT0.6S         	server_cpu=PT20M          	client_received=2000/2000	server_resps=2000	codes={200=1000, 500=1000}
                   black_hole[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=59.2%	client_mean=PT0.600870667S 	server_cpu=PT11M49.8S     	client_received=1183/2000	server_resps=1183	codes={200=1183}
                       black_hole[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=91.5%	client_mean=PT0.600005464S 	server_cpu=PT18M18S       	client_received=1830/2000	server_resps=1830	codes={200=1830}
                                 black_hole[UNLIMITED_ROUND_ROBIN].txt:	success=91.4%	client_mean=PT0.6S         	server_cpu=PT18M16.8S     	client_received=1828/2000	server_resps=1828	codes={200=1828}
                     cross_az[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=100.0%	client_mean=PT0.138584666S 	server_cpu=PT1H9M17.54S   	client_received=30000/30000	server_resps=30000	codes={200=30000}
                         cross_az[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=100.0%	client_mean=PT0.138486133S 	server_cpu=PT1H9M14.584S  	client_received=30000/30000	server_resps=30000	codes={200=30000}
                                   cross_az[UNLIMITED_ROUND_ROBIN].txt:	success=100.0%	client_mean=PT0.138486133S 	server_cpu=PT1H9M14.584S  	client_received=30000/30000	server_resps=30000	codes={200=30000}
             drastic_slowdown[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=100.0%	client_mean=PT3.120674249S 	server_cpu=PT41M7.682333314S	client_received=4000/4000	server_resps=4000	codes={200=4000}
                 drastic_slowdown[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=100.0%	client_mean=PT0.251969999S 	server_cpu=PT16M47.879999984S	client_received=4000/4000	server_resps=4000	codes={200=4000}
                           drastic_slowdown[UNLIMITED_ROUND_ROBIN].txt:	success=100.0%	client_mean=PT0.251969999S 	server_cpu=PT16M47.879999984S	client_received=4000/4000	server_resps=4000	codes={200=4000}
        fast_400s_then_revert[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=82.2%	client_mean=PT0.1022S      	server_cpu=PT10M13.2S     	client_received=6000/6000	server_resps=6000	codes={200=4932, 400=1068}
            fast_400s_then_revert[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=93.6%	client_mean=PT0.113583333S 	server_cpu=PT11M21.5S     	client_received=6000/6000	server_resps=6000	codes={200=5615, 400=385}
                      fast_400s_then_revert[UNLIMITED_ROUND_ROBIN].txt:	success=93.6%	client_mean=PT0.113583333S 	server_cpu=PT11M21.5S     	client_received=6000/6000	server_resps=6000	codes={200=5615, 400=385}
        fast_503s_then_revert[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=100.0%	client_mean=PT0.120012009S 	server_cpu=PT1H30M0.00000004S	client_received=45000/45000	server_resps=45004	codes={200=45000}
            fast_503s_then_revert[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=100.0%	client_mean=PT0.120082231S 	server_cpu=PT1H30M0.0000003S	client_received=45000/45000	server_resps=45030	codes={200=45000}
                      fast_503s_then_revert[UNLIMITED_ROUND_ROBIN].txt:	success=100.0%	client_mean=PT0.120107163S 	server_cpu=PT1H30M0.0000004S	client_received=45000/45000	server_resps=45040	codes={200=45000}
               live_reloading[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=94.3%	client_mean=PT7.1919248S   	server_cpu=PT1H55M52.15S  	client_received=2500/2500	server_resps=2500	codes={200=2357, 500=143}
                   live_reloading[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=94.1%	client_mean=PT5.5098152S   	server_cpu=PT1H56M17.99S  	client_received=2500/2500	server_resps=2500	codes={200=2352, 500=148}
                             live_reloading[UNLIMITED_ROUND_ROBIN].txt:	success=86.9%	client_mean=PT2.802124S    	server_cpu=PT1H56M45.31S  	client_received=2500/2500	server_resps=2500	codes={200=2173, 500=327}
                one_big_spike[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=100.0%	client_mean=PT2.667606696S 	server_cpu=PT2M30S        	client_received=1000/1000	server_resps=1000	codes={200=1000}
                    one_big_spike[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=100.0%	client_mean=PT1.579726259S 	server_cpu=PT2M30S        	client_received=1000/1000	server_resps=1000	codes={200=1000}
                              one_big_spike[UNLIMITED_ROUND_ROBIN].txt:	success=99.9%	client_mean=PT1.000523583S 	server_cpu=PT7M37.35S     	client_received=1000/1000	server_resps=3049	codes={200=999, 429=1}
one_endpoint_dies_on_each_server[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=63.9%	client_mean=PT9.2169728S   	server_cpu=PT25M          	client_received=2500/2500	server_resps=2500	codes={200=1597, 500=903}
 one_endpoint_dies_on_each_server[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=64.6%	client_mean=PT4.4945936S   	server_cpu=PT25M          	client_received=2500/2500	server_resps=2500	codes={200=1615, 500=885}
           one_endpoint_dies_on_each_server[UNLIMITED_ROUND_ROBIN].txt:	success=65.1%	client_mean=PT0.6S         	server_cpu=PT25M          	client_received=2500/2500	server_resps=2500	codes={200=1628, 500=872}
      server_side_rate_limits[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=100.0%	client_mean=PT17M42.307460152S	server_cpu=PT10H1M56.4S   	client_received=150000/150000	server_resps=180582	codes={200=149994, 429=6}
          server_side_rate_limits[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=100.0%	client_mean=PT0.293201887S 	server_cpu=PT9H10M31.4S   	client_received=150000/150000	server_resps=165157	codes={200=150000}
                    server_side_rate_limits[UNLIMITED_ROUND_ROBIN].txt:	success=100.0%	client_mean=PT0.217416858S 	server_cpu=PT8H45M29.2S   	client_received=150000/150000	server_resps=157646	codes={200=150000}
     short_outage_on_one_node[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=99.8%	client_mean=PT18.248406257S	server_cpu=PT53M14.00000003S	client_received=1600/1600	server_resps=1600	codes={200=1597, 500=3}
         short_outage_on_one_node[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=99.8%	client_mean=PT5.25478125S  	server_cpu=PT53M14.00000003S	client_received=1600/1600	server_resps=1600	codes={200=1597, 500=3}
                   short_outage_on_one_node[UNLIMITED_ROUND_ROBIN].txt:	success=99.6%	client_mean=PT1.9925S      	server_cpu=PT53M8.00000006S	client_received=1600/1600	server_resps=1600	codes={200=1594, 500=6}
       simplest_possible_case[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=100.0%	client_mean=PT0.834469696S 	server_cpu=PT3H3M35S      	client_received=13200/13200	server_resps=13200	codes={200=13200}
           simplest_possible_case[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=100.0%	client_mean=PT0.785757575S 	server_cpu=PT2H52M52S     	client_received=13200/13200	server_resps=13200	codes={200=13200}
                     simplest_possible_case[UNLIMITED_ROUND_ROBIN].txt:	success=100.0%	client_mean=PT0.785757575S 	server_cpu=PT2H52M52S     	client_received=13200/13200	server_resps=13200	codes={200=13200}
        slow_503s_then_revert[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=100.0%	client_mean=PT0.135264504S 	server_cpu=PT6M34.279333314S	client_received=3000/3000	server_resps=3076	codes={200=3000}
            slow_503s_then_revert[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=100.0%	client_mean=PT0.088828705S 	server_cpu=PT4M22.364666657S	client_received=3000/3000	server_resps=3031	codes={200=3000}
                      slow_503s_then_revert[UNLIMITED_ROUND_ROBIN].txt:	success=100.0%	client_mean=PT0.088828705S 	server_cpu=PT4M22.364666657S	client_received=3000/3000	server_resps=3031	codes={200=3000}
slowdown_and_error_thresholds[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=100.0%	client_mean=PT2M22.841574261S	server_cpu=PT8H1M44.433332458S	client_received=10000/10000	server_resps=10762	codes={200=10000}
    slowdown_and_error_thresholds[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=100.0%	client_mean=PT2M54.866025868S	server_cpu=PT12H4M51.993333204S	client_received=10000/10000	server_resps=11824	codes={200=9999, 500=1}
              slowdown_and_error_thresholds[UNLIMITED_ROUND_ROBIN].txt:	success=3.6%	client_mean=PT21.551691121S	server_cpu=PT54H45M57.899999949S	client_received=10000/10000	server_resps=49335	codes={200=360, 500=9640}
              uncommon_flakes[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=99.0%	client_mean=PT0.000001S    	server_cpu=PT0.01S        	client_received=10000/10000	server_resps=10000	codes={200=9900, 500=100}
                  uncommon_flakes[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=99.0%	client_mean=PT0.000001S    	server_cpu=PT0.01S        	client_received=10000/10000	server_resps=10000	codes={200=9900, 500=100}
                            uncommon_flakes[UNLIMITED_ROUND_ROBIN].txt:	success=99.0%	client_mean=PT0.000001S    	server_cpu=PT0.01S        	client_received=10000/10000	server_resps=10000	codes={200=9900, 500=100}
```



## `all_nodes_500[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/all_nodes_500[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td><td><image width=400 src="all_nodes_500[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td></tr></table>


## `all_nodes_500[CONCURRENCY_LIMITER_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/all_nodes_500[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td><td><image width=400 src="all_nodes_500[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td></tr></table>


## `all_nodes_500[UNLIMITED_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/all_nodes_500[UNLIMITED_ROUND_ROBIN].png" /></td><td><image width=400 src="all_nodes_500[UNLIMITED_ROUND_ROBIN].png" /></td></tr></table>


## `black_hole[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/black_hole[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td><td><image width=400 src="black_hole[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td></tr></table>


## `black_hole[CONCURRENCY_LIMITER_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/black_hole[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td><td><image width=400 src="black_hole[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td></tr></table>


## `black_hole[UNLIMITED_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/black_hole[UNLIMITED_ROUND_ROBIN].png" /></td><td><image width=400 src="black_hole[UNLIMITED_ROUND_ROBIN].png" /></td></tr></table>


## `cross_az[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/cross_az[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td><td><image width=400 src="cross_az[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td></tr></table>


## `cross_az[CONCURRENCY_LIMITER_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/cross_az[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td><td><image width=400 src="cross_az[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td></tr></table>


## `cross_az[UNLIMITED_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/cross_az[UNLIMITED_ROUND_ROBIN].png" /></td><td><image width=400 src="cross_az[UNLIMITED_ROUND_ROBIN].png" /></td></tr></table>


## `drastic_slowdown[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/drastic_slowdown[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td><td><image width=400 src="drastic_slowdown[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td></tr></table>


## `drastic_slowdown[CONCURRENCY_LIMITER_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/drastic_slowdown[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td><td><image width=400 src="drastic_slowdown[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td></tr></table>


## `drastic_slowdown[UNLIMITED_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/drastic_slowdown[UNLIMITED_ROUND_ROBIN].png" /></td><td><image width=400 src="drastic_slowdown[UNLIMITED_ROUND_ROBIN].png" /></td></tr></table>


## `fast_400s_then_revert[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/fast_400s_then_revert[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td><td><image width=400 src="fast_400s_then_revert[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td></tr></table>


## `fast_400s_then_revert[CONCURRENCY_LIMITER_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/fast_400s_then_revert[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td><td><image width=400 src="fast_400s_then_revert[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td></tr></table>


## `fast_400s_then_revert[UNLIMITED_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/fast_400s_then_revert[UNLIMITED_ROUND_ROBIN].png" /></td><td><image width=400 src="fast_400s_then_revert[UNLIMITED_ROUND_ROBIN].png" /></td></tr></table>


## `fast_503s_then_revert[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/fast_503s_then_revert[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td><td><image width=400 src="fast_503s_then_revert[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td></tr></table>


## `fast_503s_then_revert[CONCURRENCY_LIMITER_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/fast_503s_then_revert[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td><td><image width=400 src="fast_503s_then_revert[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td></tr></table>


## `fast_503s_then_revert[UNLIMITED_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/fast_503s_then_revert[UNLIMITED_ROUND_ROBIN].png" /></td><td><image width=400 src="fast_503s_then_revert[UNLIMITED_ROUND_ROBIN].png" /></td></tr></table>


## `live_reloading[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/live_reloading[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td><td><image width=400 src="live_reloading[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td></tr></table>


## `live_reloading[CONCURRENCY_LIMITER_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/live_reloading[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td><td><image width=400 src="live_reloading[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td></tr></table>


## `live_reloading[UNLIMITED_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/live_reloading[UNLIMITED_ROUND_ROBIN].png" /></td><td><image width=400 src="live_reloading[UNLIMITED_ROUND_ROBIN].png" /></td></tr></table>


## `one_big_spike[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/one_big_spike[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td><td><image width=400 src="one_big_spike[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td></tr></table>


## `one_big_spike[CONCURRENCY_LIMITER_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/one_big_spike[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td><td><image width=400 src="one_big_spike[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td></tr></table>


## `one_big_spike[UNLIMITED_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/one_big_spike[UNLIMITED_ROUND_ROBIN].png" /></td><td><image width=400 src="one_big_spike[UNLIMITED_ROUND_ROBIN].png" /></td></tr></table>


## `one_endpoint_dies_on_each_server[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/one_endpoint_dies_on_each_server[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td><td><image width=400 src="one_endpoint_dies_on_each_server[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td></tr></table>


## `one_endpoint_dies_on_each_server[CONCURRENCY_LIMITER_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/one_endpoint_dies_on_each_server[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td><td><image width=400 src="one_endpoint_dies_on_each_server[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td></tr></table>


## `one_endpoint_dies_on_each_server[UNLIMITED_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/one_endpoint_dies_on_each_server[UNLIMITED_ROUND_ROBIN].png" /></td><td><image width=400 src="one_endpoint_dies_on_each_server[UNLIMITED_ROUND_ROBIN].png" /></td></tr></table>


## `server_side_rate_limits[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/server_side_rate_limits[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td><td><image width=400 src="server_side_rate_limits[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td></tr></table>


## `server_side_rate_limits[CONCURRENCY_LIMITER_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/server_side_rate_limits[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td><td><image width=400 src="server_side_rate_limits[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td></tr></table>


## `server_side_rate_limits[UNLIMITED_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/server_side_rate_limits[UNLIMITED_ROUND_ROBIN].png" /></td><td><image width=400 src="server_side_rate_limits[UNLIMITED_ROUND_ROBIN].png" /></td></tr></table>


## `short_outage_on_one_node[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/short_outage_on_one_node[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td><td><image width=400 src="short_outage_on_one_node[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td></tr></table>


## `short_outage_on_one_node[CONCURRENCY_LIMITER_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/short_outage_on_one_node[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td><td><image width=400 src="short_outage_on_one_node[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td></tr></table>


## `short_outage_on_one_node[UNLIMITED_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/short_outage_on_one_node[UNLIMITED_ROUND_ROBIN].png" /></td><td><image width=400 src="short_outage_on_one_node[UNLIMITED_ROUND_ROBIN].png" /></td></tr></table>


## `simplest_possible_case[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/simplest_possible_case[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td><td><image width=400 src="simplest_possible_case[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td></tr></table>


## `simplest_possible_case[CONCURRENCY_LIMITER_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/simplest_possible_case[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td><td><image width=400 src="simplest_possible_case[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td></tr></table>


## `simplest_possible_case[UNLIMITED_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/simplest_possible_case[UNLIMITED_ROUND_ROBIN].png" /></td><td><image width=400 src="simplest_possible_case[UNLIMITED_ROUND_ROBIN].png" /></td></tr></table>


## `slow_503s_then_revert[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/slow_503s_then_revert[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td><td><image width=400 src="slow_503s_then_revert[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td></tr></table>


## `slow_503s_then_revert[CONCURRENCY_LIMITER_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/slow_503s_then_revert[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td><td><image width=400 src="slow_503s_then_revert[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td></tr></table>


## `slow_503s_then_revert[UNLIMITED_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/slow_503s_then_revert[UNLIMITED_ROUND_ROBIN].png" /></td><td><image width=400 src="slow_503s_then_revert[UNLIMITED_ROUND_ROBIN].png" /></td></tr></table>


## `slowdown_and_error_thresholds[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/slowdown_and_error_thresholds[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td><td><image width=400 src="slowdown_and_error_thresholds[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td></tr></table>


## `slowdown_and_error_thresholds[CONCURRENCY_LIMITER_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/slowdown_and_error_thresholds[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td><td><image width=400 src="slowdown_and_error_thresholds[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td></tr></table>


## `slowdown_and_error_thresholds[UNLIMITED_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/slowdown_and_error_thresholds[UNLIMITED_ROUND_ROBIN].png" /></td><td><image width=400 src="slowdown_and_error_thresholds[UNLIMITED_ROUND_ROBIN].png" /></td></tr></table>


## `uncommon_flakes[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/uncommon_flakes[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td><td><image width=400 src="uncommon_flakes[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td></tr></table>


## `uncommon_flakes[CONCURRENCY_LIMITER_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/uncommon_flakes[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td><td><image width=400 src="uncommon_flakes[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td></tr></table>


## `uncommon_flakes[UNLIMITED_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/uncommon_flakes[UNLIMITED_ROUND_ROBIN].png" /></td><td><image width=400 src="uncommon_flakes[UNLIMITED_ROUND_ROBIN].png" /></td></tr></table>


