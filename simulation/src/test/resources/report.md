# Report
<!-- Run SimulationTest to regenerate this report. -->
```
          all_nodes_500[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN].txt:	success=66.3%	client_mean=PT1.86201S     	server_cpu=PT19M41.7S     	client_received=2000/2000	server_resps=2000	codes={200=1326, 500=674}
                all_nodes_500[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=50.0%	client_mean=PT0.59085S     	server_cpu=PT19M41.7S     	client_received=2000/2000	server_resps=2000	codes={200=1000, 500=1000}
                    all_nodes_500[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=50.0%	client_mean=PT0.59085S     	server_cpu=PT19M41.7S     	client_received=2000/2000	server_resps=2000	codes={200=1000, 500=1000}
                              all_nodes_500[UNLIMITED_ROUND_ROBIN].txt:	success=50.0%	client_mean=PT0.59085S     	server_cpu=PT19M41.7S     	client_received=2000/2000	server_resps=2000	codes={200=1000, 500=1000}
             black_hole[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN].txt:	success=89.9%	client_mean=PT0.57988592S  	server_cpu=PT17M21.9S     	client_received=1797/2000	server_resps=1797	codes={200=1797}
                   black_hole[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=88.7%	client_mean=PT0.579717992S 	server_cpu=PT17M7.5S      	client_received=1773/2000	server_resps=1773	codes={200=1773}
                       black_hole[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=89.9%	client_mean=PT0.57988592S  	server_cpu=PT17M21.9S     	client_received=1797/2000	server_resps=1797	codes={200=1797}
                                 black_hole[UNLIMITED_ROUND_ROBIN].txt:	success=65.0%	client_mean=PT0.586343341S 	server_cpu=PT12M41.66S    	client_received=1299/2000	server_resps=1299	codes={200=1299}
       drastic_slowdown[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN].txt:	success=100.0%	client_mean=PT0.85580125S  	server_cpu=PT57M3.205S    	client_received=4000/4000	server_resps=4000	codes={200=4000}
             drastic_slowdown[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=100.0%	client_mean=PT0.86453125S  	server_cpu=PT57M38.125S   	client_received=4000/4000	server_resps=4000	codes={200=4000}
                 drastic_slowdown[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=100.0%	client_mean=PT0.85580125S  	server_cpu=PT57M3.205S    	client_received=4000/4000	server_resps=4000	codes={200=4000}
                           drastic_slowdown[UNLIMITED_ROUND_ROBIN].txt:	success=100.0%	client_mean=PT2.44372125S  	server_cpu=PT2H42M54.885S 	client_received=4000/4000	server_resps=4000	codes={200=4000}
  fast_500s_then_revert[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN].txt:	success=98.1%	client_mean=PT0.071725866S 	server_cpu=PT4M28.972S    	client_received=3750/3750	server_resps=3750	codes={200=3679, 500=71}
        fast_500s_then_revert[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=99.7%	client_mean=PT0.079397333S 	server_cpu=PT4M57.74S     	client_received=3750/3750	server_resps=3750	codes={200=3739, 500=11}
            fast_500s_then_revert[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=76.7%	client_mean=PT0.053707733S 	server_cpu=PT3M21.404S    	client_received=3750/3750	server_resps=3750	codes={200=2876, 500=874}
                      fast_500s_then_revert[UNLIMITED_ROUND_ROBIN].txt:	success=76.7%	client_mean=PT0.053707733S 	server_cpu=PT3M21.404S    	client_received=3750/3750	server_resps=3750	codes={200=2876, 500=874}
         live_reloading[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN].txt:	success=84.9%	client_mean=PT3.2184032S   	server_cpu=PT1H7M31.656S  	client_received=2500/2500	server_resps=2500	codes={200=2123, 500=377}
               live_reloading[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=59.0%	client_mean=PT2.93804S     	server_cpu=PT1H34M6.156S  	client_received=2500/2500	server_resps=2500	codes={200=1474, 500=1026}
                   live_reloading[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=58.6%	client_mean=PT2.9248512S   	server_cpu=PT1H33M32.58S  	client_received=2500/2500	server_resps=2500	codes={200=1465, 500=1035}
                             live_reloading[UNLIMITED_ROUND_ROBIN].txt:	success=58.4%	client_mean=PT2.3889216S   	server_cpu=PT1H39M32.304S 	client_received=2500/2500	server_resps=2500	codes={200=1461, 500=1039}
one_endpoint_dies_on_each_server[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN].txt:	success=42.6%	client_mean=PT1.31733431S  	server_cpu=PT13M22.32S    	client_received=1364/2500	server_resps=1364	codes={200=1066, 500=298}
one_endpoint_dies_on_each_server[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=63.8%	client_mean=PT0.58188S     	server_cpu=PT24M14.7S     	client_received=2500/2500	server_resps=2500	codes={200=1594, 500=906}
 one_endpoint_dies_on_each_server[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=65.5%	client_mean=PT0.58188S     	server_cpu=PT24M14.7S     	client_received=2500/2500	server_resps=2500	codes={200=1638, 500=862}
           one_endpoint_dies_on_each_server[UNLIMITED_ROUND_ROBIN].txt:	success=65.5%	client_mean=PT0.58188S     	server_cpu=PT24M14.7S     	client_received=2500/2500	server_resps=2500	codes={200=1638, 500=862}
 simplest_possible_case[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN].txt:	success=100.0%	client_mean=PT0.757203849S 	server_cpu=PT2H46M35.09080914S	client_received=13200/13200	server_resps=13200	codes={200=13200}
       simplest_possible_case[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=100.0%	client_mean=PT0.997975196S 	server_cpu=PT3H39M33.27259554S	client_received=13200/13200	server_resps=13200	codes={200=13200}
           simplest_possible_case[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=100.0%	client_mean=PT0.757203849S 	server_cpu=PT2H46M35.09080914S	client_received=13200/13200	server_resps=13200	codes={200=13200}
                     simplest_possible_case[UNLIMITED_ROUND_ROBIN].txt:	success=100.0%	client_mean=PT0.757203849S 	server_cpu=PT2H46M35.09080914S	client_received=13200/13200	server_resps=13200	codes={200=13200}
  slow_503s_then_revert[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN].txt:	success=100.0%	client_mean=PT0.292731666S 	server_cpu=PT14M38.195S   	client_received=3000/3000	server_resps=3175	codes={200=3000}
        slow_503s_then_revert[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=100.0%	client_mean=PT0.335935S    	server_cpu=PT16M47.805S   	client_received=3000/3000	server_resps=3197	codes={200=3000}
            slow_503s_then_revert[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=100.0%	client_mean=PT0.733185S    	server_cpu=PT36M39.555S   	client_received=3000/3000	server_resps=3416	codes={200=3000}
                      slow_503s_then_revert[UNLIMITED_ROUND_ROBIN].txt:	success=100.0%	client_mean=PT1.406521666S 	server_cpu=PT1H10M19.565S 	client_received=3000/3000	server_resps=3810	codes={200=3000}
slowdown_and_error_thresholds[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN].txt:	success=12.3%	client_mean=PT9.156086414S 	server_cpu=PT1H22M10.65S  	client_received=2407/10000	server_resps=2407	codes={200=1227, 500=1180}
slowdown_and_error_thresholds[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=1.6%	client_mean=PT7.337106S    	server_cpu=PT3H41M38.344S 	client_received=10000/10000	server_resps=10000	codes={200=163, 500=9837}
    slowdown_and_error_thresholds[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=1.2%	client_mean=PT7.3037772S   	server_cpu=PT3H44M17.984S 	client_received=10000/10000	server_resps=10000	codes={200=120, 500=9880}
              slowdown_and_error_thresholds[UNLIMITED_ROUND_ROBIN].txt:	success=1.2%	client_mean=PT3.5573984S   	server_cpu=PT9H52M53.984S 	client_received=10000/10000	server_resps=10000	codes={200=120, 500=9880}
        uncommon_flakes[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN].txt:	success=97.9%	client_mean=PT0.09807059S  	server_cpu=PT0S           	client_received=9888/10000	server_resps=9888	codes={200=9789, 500=99}
              uncommon_flakes[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=99.0%	client_mean=PT0S           	server_cpu=PT0S           	client_received=10000/10000	server_resps=10000	codes={200=9902, 500=98}
                  uncommon_flakes[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=99.0%	client_mean=PT0S           	server_cpu=PT0S           	client_received=10000/10000	server_resps=10000	codes={200=9900, 500=100}
                            uncommon_flakes[UNLIMITED_ROUND_ROBIN].txt:	success=99.0%	client_mean=PT0S           	server_cpu=PT0S           	client_received=10000/10000	server_resps=10000	codes={200=9900, 500=100}
```



## `all_nodes_500[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/all_nodes_500[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN].png" /></td><td><image width=400 src="all_nodes_500[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN].png" /></td></tr></table>


## `all_nodes_500[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/all_nodes_500[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td><td><image width=400 src="all_nodes_500[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td></tr></table>


## `all_nodes_500[CONCURRENCY_LIMITER_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/all_nodes_500[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td><td><image width=400 src="all_nodes_500[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td></tr></table>


## `all_nodes_500[UNLIMITED_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/all_nodes_500[UNLIMITED_ROUND_ROBIN].png" /></td><td><image width=400 src="all_nodes_500[UNLIMITED_ROUND_ROBIN].png" /></td></tr></table>


## `black_hole[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/black_hole[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN].png" /></td><td><image width=400 src="black_hole[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN].png" /></td></tr></table>


## `black_hole[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/black_hole[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td><td><image width=400 src="black_hole[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td></tr></table>


## `black_hole[CONCURRENCY_LIMITER_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/black_hole[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td><td><image width=400 src="black_hole[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td></tr></table>


## `black_hole[UNLIMITED_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/black_hole[UNLIMITED_ROUND_ROBIN].png" /></td><td><image width=400 src="black_hole[UNLIMITED_ROUND_ROBIN].png" /></td></tr></table>


## `drastic_slowdown[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/drastic_slowdown[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN].png" /></td><td><image width=400 src="drastic_slowdown[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN].png" /></td></tr></table>


## `drastic_slowdown[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/drastic_slowdown[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td><td><image width=400 src="drastic_slowdown[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td></tr></table>


## `drastic_slowdown[CONCURRENCY_LIMITER_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/drastic_slowdown[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td><td><image width=400 src="drastic_slowdown[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td></tr></table>


## `drastic_slowdown[UNLIMITED_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/drastic_slowdown[UNLIMITED_ROUND_ROBIN].png" /></td><td><image width=400 src="drastic_slowdown[UNLIMITED_ROUND_ROBIN].png" /></td></tr></table>


## `fast_500s_then_revert[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/fast_500s_then_revert[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN].png" /></td><td><image width=400 src="fast_500s_then_revert[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN].png" /></td></tr></table>


## `fast_500s_then_revert[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/fast_500s_then_revert[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td><td><image width=400 src="fast_500s_then_revert[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td></tr></table>


## `fast_500s_then_revert[CONCURRENCY_LIMITER_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/fast_500s_then_revert[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td><td><image width=400 src="fast_500s_then_revert[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td></tr></table>


## `fast_500s_then_revert[UNLIMITED_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/fast_500s_then_revert[UNLIMITED_ROUND_ROBIN].png" /></td><td><image width=400 src="fast_500s_then_revert[UNLIMITED_ROUND_ROBIN].png" /></td></tr></table>


## `live_reloading[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/live_reloading[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN].png" /></td><td><image width=400 src="live_reloading[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN].png" /></td></tr></table>


## `live_reloading[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/live_reloading[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td><td><image width=400 src="live_reloading[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td></tr></table>


## `live_reloading[CONCURRENCY_LIMITER_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/live_reloading[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td><td><image width=400 src="live_reloading[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td></tr></table>


## `live_reloading[UNLIMITED_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/live_reloading[UNLIMITED_ROUND_ROBIN].png" /></td><td><image width=400 src="live_reloading[UNLIMITED_ROUND_ROBIN].png" /></td></tr></table>


## `one_endpoint_dies_on_each_server[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/one_endpoint_dies_on_each_server[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN].png" /></td><td><image width=400 src="one_endpoint_dies_on_each_server[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN].png" /></td></tr></table>


## `one_endpoint_dies_on_each_server[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/one_endpoint_dies_on_each_server[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td><td><image width=400 src="one_endpoint_dies_on_each_server[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td></tr></table>


## `one_endpoint_dies_on_each_server[CONCURRENCY_LIMITER_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/one_endpoint_dies_on_each_server[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td><td><image width=400 src="one_endpoint_dies_on_each_server[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td></tr></table>


## `one_endpoint_dies_on_each_server[UNLIMITED_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/one_endpoint_dies_on_each_server[UNLIMITED_ROUND_ROBIN].png" /></td><td><image width=400 src="one_endpoint_dies_on_each_server[UNLIMITED_ROUND_ROBIN].png" /></td></tr></table>


## `simplest_possible_case[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/simplest_possible_case[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN].png" /></td><td><image width=400 src="simplest_possible_case[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN].png" /></td></tr></table>


## `simplest_possible_case[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/simplest_possible_case[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td><td><image width=400 src="simplest_possible_case[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td></tr></table>


## `simplest_possible_case[CONCURRENCY_LIMITER_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/simplest_possible_case[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td><td><image width=400 src="simplest_possible_case[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td></tr></table>


## `simplest_possible_case[UNLIMITED_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/simplest_possible_case[UNLIMITED_ROUND_ROBIN].png" /></td><td><image width=400 src="simplest_possible_case[UNLIMITED_ROUND_ROBIN].png" /></td></tr></table>


## `slow_503s_then_revert[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/slow_503s_then_revert[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN].png" /></td><td><image width=400 src="slow_503s_then_revert[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN].png" /></td></tr></table>


## `slow_503s_then_revert[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/slow_503s_then_revert[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td><td><image width=400 src="slow_503s_then_revert[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td></tr></table>


## `slow_503s_then_revert[CONCURRENCY_LIMITER_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/slow_503s_then_revert[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td><td><image width=400 src="slow_503s_then_revert[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td></tr></table>


## `slow_503s_then_revert[UNLIMITED_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/slow_503s_then_revert[UNLIMITED_ROUND_ROBIN].png" /></td><td><image width=400 src="slow_503s_then_revert[UNLIMITED_ROUND_ROBIN].png" /></td></tr></table>


## `slowdown_and_error_thresholds[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/slowdown_and_error_thresholds[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN].png" /></td><td><image width=400 src="slowdown_and_error_thresholds[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN].png" /></td></tr></table>


## `slowdown_and_error_thresholds[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/slowdown_and_error_thresholds[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td><td><image width=400 src="slowdown_and_error_thresholds[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td></tr></table>


## `slowdown_and_error_thresholds[CONCURRENCY_LIMITER_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/slowdown_and_error_thresholds[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td><td><image width=400 src="slowdown_and_error_thresholds[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td></tr></table>


## `slowdown_and_error_thresholds[UNLIMITED_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/slowdown_and_error_thresholds[UNLIMITED_ROUND_ROBIN].png" /></td><td><image width=400 src="slowdown_and_error_thresholds[UNLIMITED_ROUND_ROBIN].png" /></td></tr></table>


## `uncommon_flakes[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/uncommon_flakes[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN].png" /></td><td><image width=400 src="uncommon_flakes[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN].png" /></td></tr></table>


## `uncommon_flakes[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/uncommon_flakes[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td><td><image width=400 src="uncommon_flakes[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td></tr></table>


## `uncommon_flakes[CONCURRENCY_LIMITER_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/uncommon_flakes[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td><td><image width=400 src="uncommon_flakes[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td></tr></table>


## `uncommon_flakes[UNLIMITED_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/uncommon_flakes[UNLIMITED_ROUND_ROBIN].png" /></td><td><image width=400 src="uncommon_flakes[UNLIMITED_ROUND_ROBIN].png" /></td></tr></table>


