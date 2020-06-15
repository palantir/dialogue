# Report
<!-- Run SimulationTest to regenerate this report. -->
```
                all_nodes_500[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=79.3%	client_mean=PT5.81342S     	server_cpu=PT20M          	client_received=2000/2000	server_resps=2000	codes={200=1586, 500=414}
                    all_nodes_500[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=74.9%	client_mean=PT3.337485S    	server_cpu=PT20M54S       	client_received=2000/2000	server_resps=2090	codes={200=1497, 500=503}
                              all_nodes_500[UNLIMITED_ROUND_ROBIN].txt:	success=50.0%	client_mean=PT0.6S         	server_cpu=PT23M36S       	client_received=2000/2000	server_resps=2360	codes={200=1000, 500=1000}
                   black_hole[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=59.2%	client_mean=PT0.600655114S 	server_cpu=PT11M49.8S     	client_received=1183/2000	server_resps=1183	codes={200=1183}
                       black_hole[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=91.6%	client_mean=PT0.600005461S 	server_cpu=PT18M48.6S     	client_received=1831/2000	server_resps=1881	codes={200=1831}
                                 black_hole[UNLIMITED_ROUND_ROBIN].txt:	success=91.4%	client_mean=PT0.6S         	server_cpu=PT18M46.8S     	client_received=1828/2000	server_resps=1878	codes={200=1828}
                     cross_az[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=100.0%	client_mean=PT0.138584666S 	server_cpu=PT1H9M17.54S   	client_received=30000/30000	server_resps=30000	codes={200=30000}
                         cross_az[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=100.0%	client_mean=PT0.138103933S 	server_cpu=PT1H9M7.615S   	client_received=30000/30000	server_resps=32998	codes={200=30000}
                                   cross_az[UNLIMITED_ROUND_ROBIN].txt:	success=100.0%	client_mean=PT0.138103933S 	server_cpu=PT1H9M7.615S   	client_received=30000/30000	server_resps=32998	codes={200=30000}
             drastic_slowdown[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=100.0%	client_mean=PT2.947028083S 	server_cpu=PT41M8.862333314S	client_received=4000/4000	server_resps=4000	codes={200=4000}
                 drastic_slowdown[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=100.0%	client_mean=PT0.258462416S 	server_cpu=PT19M37.682666648S	client_received=4000/4000	server_resps=4134	codes={200=4000}
                           drastic_slowdown[UNLIMITED_ROUND_ROBIN].txt:	success=100.0%	client_mean=PT0.258462416S 	server_cpu=PT19M37.682666648S	client_received=4000/4000	server_resps=4134	codes={200=4000}
        fast_400s_then_revert[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=82.2%	client_mean=PT0.1022S      	server_cpu=PT10M13.2S     	client_received=6000/6000	server_resps=6000	codes={200=4932, 400=1068}
            fast_400s_then_revert[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=87.2%	client_mean=PT0.10715S     	server_cpu=PT12M29.22S    	client_received=6000/6000	server_resps=7096	codes={200=5229, 400=771}
                      fast_400s_then_revert[UNLIMITED_ROUND_ROBIN].txt:	success=87.2%	client_mean=PT0.10715S     	server_cpu=PT12M29.22S    	client_received=6000/6000	server_resps=7096	codes={200=5229, 400=771}
        fast_503s_then_revert[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=100.0%	client_mean=PT0.120012009S 	server_cpu=PT1H30M0.00000004S	client_received=45000/45000	server_resps=45004	codes={200=45000}
            fast_503s_then_revert[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=100.0%	client_mean=PT0.120130926S 	server_cpu=PT1H32M23.40000607S	client_received=45000/45000	server_resps=46802	codes={200=45000}
                      fast_503s_then_revert[UNLIMITED_ROUND_ROBIN].txt:	success=100.0%	client_mean=PT0.120130926S 	server_cpu=PT1H32M23.40000607S	client_received=45000/45000	server_resps=46802	codes={200=45000}
               live_reloading[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=94.3%	client_mean=PT6.987708S    	server_cpu=PT1H56M8.59S   	client_received=2500/2500	server_resps=2500	codes={200=2357, 500=143}
                   live_reloading[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=84.4%	client_mean=PT4.8709216S   	server_cpu=PT1H58M9.87S   	client_received=2500/2500	server_resps=2595	codes={200=2110, 500=390}
                             live_reloading[UNLIMITED_ROUND_ROBIN].txt:	success=83.8%	client_mean=PT2.83704S     	server_cpu=PT2H2M49.5S    	client_received=2500/2500	server_resps=2600	codes={200=2096, 500=404}
                one_big_spike[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=100.0%	client_mean=PT2.569766928S 	server_cpu=PT2M30S        	client_received=1000/1000	server_resps=1000	codes={200=1000}
                    one_big_spike[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=100.0%	client_mean=PT1.521339525S 	server_cpu=PT2M30S        	client_received=1000/1000	server_resps=1000	codes={200=1000}
                              one_big_spike[UNLIMITED_ROUND_ROBIN].txt:	success=99.9%	client_mean=PT1.000788286S 	server_cpu=PT7M38.7S      	client_received=1000/1000	server_resps=3056	codes={200=999, 429=1}
one_endpoint_dies_on_each_server[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=64.9%	client_mean=PT8.1950896S   	server_cpu=PT25M          	client_received=2500/2500	server_resps=2500	codes={200=1623, 500=877}
 one_endpoint_dies_on_each_server[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=65.4%	client_mean=PT4.0117872S   	server_cpu=PT25M          	client_received=2500/2500	server_resps=2500	codes={200=1636, 500=864}
           one_endpoint_dies_on_each_server[UNLIMITED_ROUND_ROBIN].txt:	success=65.1%	client_mean=PT0.6S         	server_cpu=PT25M          	client_received=2500/2500	server_resps=2500	codes={200=1628, 500=872}
      server_side_rate_limits[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=100.0%	client_mean=PT12M44.762591546S	server_cpu=PT9H55M40.4S   	client_received=150000/150000	server_resps=178702	codes={200=149989, 429=11}
          server_side_rate_limits[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=100.0%	client_mean=PT0.370014292S 	server_cpu=PT9H43M16.8S   	client_received=150000/150000	server_resps=174984	codes={200=149998, 429=2}
                    server_side_rate_limits[UNLIMITED_ROUND_ROBIN].txt:	success=99.3%	client_mean=PT0.267175177S 	server_cpu=PT10H11M16.8S  	client_received=150000/150000	server_resps=183384	codes={200=148877, 429=1123}
       simplest_possible_case[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=100.0%	client_mean=PT0.834469696S 	server_cpu=PT3H3M35S      	client_received=13200/13200	server_resps=13200	codes={200=13200}
           simplest_possible_case[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=100.0%	client_mean=PT0.610333333S 	server_cpu=PT6H26M35.6S   	client_received=13200/13200	server_resps=32117	codes={200=13200}
                     simplest_possible_case[UNLIMITED_ROUND_ROBIN].txt:	success=100.0%	client_mean=PT0.610333333S 	server_cpu=PT6H26M35.6S   	client_received=13200/13200	server_resps=32117	codes={200=13200}
        slow_503s_then_revert[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=100.0%	client_mean=PT0.135264504S 	server_cpu=PT6M34.279333314S	client_received=3000/3000	server_resps=3076	codes={200=3000}
            slow_503s_then_revert[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=100.0%	client_mean=PT0.091666569S 	server_cpu=PT5M59.069333304S	client_received=3000/3000	server_resps=3285	codes={200=3000}
                      slow_503s_then_revert[UNLIMITED_ROUND_ROBIN].txt:	success=100.0%	client_mean=PT0.091666569S 	server_cpu=PT5M59.069333304S	client_received=3000/3000	server_resps=3285	codes={200=3000}
slowdown_and_error_thresholds[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=100.0%	client_mean=PT2M28.388896928S	server_cpu=PT8H48M18.546665835S	client_received=10000/10000	server_resps=10899	codes={200=10000}
    slowdown_and_error_thresholds[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=100.0%	client_mean=PT3M24.308654421S	server_cpu=PT14H3M44.353333281S	client_received=10000/10000	server_resps=13383	codes={200=9999, 500=1}
              slowdown_and_error_thresholds[UNLIMITED_ROUND_ROBIN].txt:	success=3.6%	client_mean=PT21.551691121S	server_cpu=PT54H45M57.899999949S	client_received=10000/10000	server_resps=49335	codes={200=360, 500=9640}
              uncommon_flakes[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=99.0%	client_mean=PT0.000001S    	server_cpu=PT0.01S        	client_received=10000/10000	server_resps=10000	codes={200=9900, 500=100}
                  uncommon_flakes[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=99.0%	client_mean=PT0.000001S    	server_cpu=PT0.01018S     	client_received=10000/10000	server_resps=10180	codes={200=9900, 500=100}
                            uncommon_flakes[UNLIMITED_ROUND_ROBIN].txt:	success=99.0%	client_mean=PT0.000001S    	server_cpu=PT0.01018S     	client_received=10000/10000	server_resps=10180	codes={200=9900, 500=100}
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


