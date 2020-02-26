# Report
<!-- Run SimulationTest to regenerate this report. -->
```
          all_nodes_500[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN].txt:	success=66.3%	client_mean=PT1.87116S     	server_cpu=PT20M          	client_received=2000/2000	server_resps=2000	codes={200=1326, 500=674}
                all_nodes_500[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=50.0%	client_mean=PT0.6S         	server_cpu=PT20M          	client_received=2000/2000	server_resps=2000	codes={200=1000, 500=1000}
                    all_nodes_500[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=50.0%	client_mean=PT0.6S         	server_cpu=PT20M          	client_received=2000/2000	server_resps=2000	codes={200=1000, 500=1000}
                                      all_nodes_500[PREFER_LOWEST].txt:	success=66.3%	client_mean=PT1.87116S     	server_cpu=PT20M          	client_received=2000/2000	server_resps=2000	codes={200=1326, 500=674}
                              all_nodes_500[UNLIMITED_ROUND_ROBIN].txt:	success=50.0%	client_mean=PT0.6S         	server_cpu=PT20M          	client_received=2000/2000	server_resps=2000	codes={200=1000, 500=1000}
             black_hole[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN].txt:	success=89.9%	client_mean=PT0.600086254S 	server_cpu=PT17M58.2S     	client_received=1797/2000	server_resps=1797	codes={200=1797}
                   black_hole[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=88.7%	client_mean=PT0.600191765S 	server_cpu=PT17M43.8S     	client_received=1773/2000	server_resps=1773	codes={200=1773}
                       black_hole[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=89.9%	client_mean=PT0.600086254S 	server_cpu=PT17M58.2S     	client_received=1797/2000	server_resps=1797	codes={200=1797}
                                         black_hole[PREFER_LOWEST].txt:	success=91.3%	client_mean=PT0.6S         	server_cpu=PT18M15.6S     	client_received=1826/2000	server_resps=1826	codes={200=1826}
                                 black_hole[UNLIMITED_ROUND_ROBIN].txt:	success=65.0%	client_mean=PT0.6S         	server_cpu=PT12M59.4S     	client_received=1299/2000	server_resps=1299	codes={200=1299}
       drastic_slowdown[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN].txt:	success=100.0%	client_mean=PT2.069939083S 	server_cpu=PT2H17M59.756333311S	client_received=4000/4000	server_resps=4000	codes={200=4000}
             drastic_slowdown[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=100.0%	client_mean=PT2.053277999S 	server_cpu=PT2H16M53.111999959S	client_received=4000/4000	server_resps=4000	codes={200=4000}
                 drastic_slowdown[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=100.0%	client_mean=PT2.069939083S 	server_cpu=PT2H17M59.756333311S	client_received=4000/4000	server_resps=4000	codes={200=4000}
                                   drastic_slowdown[PREFER_LOWEST].txt:	success=100.0%	client_mean=PT0.270818666S 	server_cpu=PT18M3.27466665S	client_received=4000/4000	server_resps=4000	codes={200=4000}
                           drastic_slowdown[UNLIMITED_ROUND_ROBIN].txt:	success=100.0%	client_mean=PT8.353421749S 	server_cpu=PT9H16M53.686999978S	client_received=4000/4000	server_resps=4000	codes={200=4000}
  fast_500s_then_revert[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN].txt:	success=98.1%	client_mean=PT0.073223422S 	server_cpu=PT4M34.587833291S	client_received=3750/3750	server_resps=3750	codes={200=3679, 500=71}
        fast_500s_then_revert[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=99.7%	client_mean=PT0.080628266S 	server_cpu=PT5M2.355999997S	client_received=3750/3750	server_resps=3750	codes={200=3739, 500=11}
            fast_500s_then_revert[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=76.7%	client_mean=PT0.055463644S 	server_cpu=PT3M27.988666346S	client_received=3750/3750	server_resps=3750	codes={200=2876, 500=874}
                              fast_500s_then_revert[PREFER_LOWEST].txt:	success=97.9%	client_mean=PT0.073042755S 	server_cpu=PT4M33.910333287S	client_received=3750/3750	server_resps=3750	codes={200=3673, 500=77}
                      fast_500s_then_revert[UNLIMITED_ROUND_ROBIN].txt:	success=76.7%	client_mean=PT0.055463644S 	server_cpu=PT3M27.988666346S	client_received=3750/3750	server_resps=3750	codes={200=2876, 500=874}
         live_reloading[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN].txt:	success=82.4%	client_mean=PT4.5664688S   	server_cpu=PT1H52M14.26S  	client_received=2500/2500	server_resps=2500	codes={200=2061, 500=439}
               live_reloading[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=59.0%	client_mean=PT3.5693656S   	server_cpu=PT1H58M42.9S   	client_received=2500/2500	server_resps=2500	codes={200=1476, 500=1024}
                   live_reloading[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=58.6%	client_mean=PT3.5376608S   	server_cpu=PT1H58M19S     	client_received=2500/2500	server_resps=2500	codes={200=1466, 500=1034}
                                     live_reloading[PREFER_LOWEST].txt:	success=82.3%	client_mean=PT4.572344S    	server_cpu=PT1H51M41.76S  	client_received=2500/2500	server_resps=2500	codes={200=2057, 500=443}
                             live_reloading[UNLIMITED_ROUND_ROBIN].txt:	success=58.4%	client_mean=PT2.8396S      	server_cpu=PT1H58M19S     	client_received=2500/2500	server_resps=2500	codes={200=1461, 500=1039}
one_endpoint_dies_on_each_server[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN].txt:	success=65.8%	client_mean=PT6.0343024S   	server_cpu=PT25M          	client_received=2500/2500	server_resps=2500	codes={200=1645, 500=855}
one_endpoint_dies_on_each_server[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=63.8%	client_mean=PT0.6S         	server_cpu=PT25M          	client_received=2500/2500	server_resps=2500	codes={200=1594, 500=906}
 one_endpoint_dies_on_each_server[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=65.5%	client_mean=PT0.6S         	server_cpu=PT25M          	client_received=2500/2500	server_resps=2500	codes={200=1638, 500=862}
                   one_endpoint_dies_on_each_server[PREFER_LOWEST].txt:	success=66.4%	client_mean=PT5.9595648S   	server_cpu=PT25M          	client_received=2500/2500	server_resps=2500	codes={200=1659, 500=841}
           one_endpoint_dies_on_each_server[UNLIMITED_ROUND_ROBIN].txt:	success=65.5%	client_mean=PT0.6S         	server_cpu=PT25M          	client_received=2500/2500	server_resps=2500	codes={200=1638, 500=862}
 simplest_possible_case[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN].txt:	success=100.0%	client_mean=PT0.799939393S 	server_cpu=PT2H55M59.2S   	client_received=13200/13200	server_resps=13200	codes={200=13200}
       simplest_possible_case[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=100.0%	client_mean=PT0.998696969S 	server_cpu=PT3H39M42.8S   	client_received=13200/13200	server_resps=13200	codes={200=13200}
           simplest_possible_case[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=100.0%	client_mean=PT0.799939393S 	server_cpu=PT2H55M59.2S   	client_received=13200/13200	server_resps=13200	codes={200=13200}
                             simplest_possible_case[PREFER_LOWEST].txt:	success=100.0%	client_mean=PT0.785787878S 	server_cpu=PT2H52M52.4S   	client_received=13200/13200	server_resps=13200	codes={200=13200}
                     simplest_possible_case[UNLIMITED_ROUND_ROBIN].txt:	success=100.0%	client_mean=PT0.799939393S 	server_cpu=PT2H55M59.2S   	client_received=13200/13200	server_resps=13200	codes={200=13200}
  slow_503s_then_revert[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN].txt:	success=100.0%	client_mean=PT0.294783777S 	server_cpu=PT14M44.351333306S	client_received=3000/3000	server_resps=3175	codes={200=3000}
        slow_503s_then_revert[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=100.0%	client_mean=PT0.337337888S 	server_cpu=PT16M52.013666631S	client_received=3000/3000	server_resps=3197	codes={200=3000}
            slow_503s_then_revert[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=100.0%	client_mean=PT0.736112444S 	server_cpu=PT36M48.33733331S	client_received=3000/3000	server_resps=3416	codes={200=3000}
                              slow_503s_then_revert[PREFER_LOWEST].txt:	success=100.0%	client_mean=PT0.128283333S 	server_cpu=PT6M24.84999994S	client_received=3000/3000	server_resps=3127	codes={200=3000}
                      slow_503s_then_revert[UNLIMITED_ROUND_ROBIN].txt:	success=100.0%	client_mean=PT1.410522888S 	server_cpu=PT1H10M31.568666642S	client_received=3000/3000	server_resps=3810	codes={200=3000}
slowdown_and_error_thresholds[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN].txt:	success=23.9%	client_mean=PT1M35.336218332S	server_cpu=PT10H15M50.86399988S	client_received=10000/10000	server_resps=10000	codes={200=2388, 500=7612}
slowdown_and_error_thresholds[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=1.4%	client_mean=PT20.017261999S	server_cpu=PT10H35M25.600666645S	client_received=10000/10000	server_resps=10000	codes={200=142, 500=9858}
    slowdown_and_error_thresholds[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=1.2%	client_mean=PT16.859225466S	server_cpu=PT10H30M49.207333306S	client_received=10000/10000	server_resps=10000	codes={200=120, 500=9880}
                      slowdown_and_error_thresholds[PREFER_LOWEST].txt:	success=25.4%	client_mean=PT1M43.154915999S	server_cpu=PT9H45M26.966666494S	client_received=10000/10000	server_resps=10000	codes={200=2535, 500=7465}
              slowdown_and_error_thresholds[UNLIMITED_ROUND_ROBIN].txt:	success=1.2%	client_mean=PT3.974119999S 	server_cpu=PT11H2M21.19999998S	client_received=10000/10000	server_resps=10000	codes={200=120, 500=9880}
        uncommon_flakes[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN].txt:	success=97.9%	client_mean=PT0.098022879S 	server_cpu=PT0.010031S    	client_received=9893/10000	server_resps=9893	codes={200=9794, 500=99}
              uncommon_flakes[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=99.0%	client_mean=PT0.000001S    	server_cpu=PT0.01S        	client_received=10000/10000	server_resps=10000	codes={200=9902, 500=98}
                  uncommon_flakes[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=99.0%	client_mean=PT0.000001S    	server_cpu=PT0.01S        	client_received=10000/10000	server_resps=10000	codes={200=9900, 500=100}
                                    uncommon_flakes[PREFER_LOWEST].txt:	success=98.2%	client_mean=PT0.106900307S 	server_cpu=PT0.010034S    	client_received=9920/10000	server_resps=9920	codes={200=9822, 500=98}
                            uncommon_flakes[UNLIMITED_ROUND_ROBIN].txt:	success=99.0%	client_mean=PT0.000001S    	server_cpu=PT0.01S        	client_received=10000/10000	server_resps=10000	codes={200=9900, 500=100}
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


## `all_nodes_500[PREFER_LOWEST]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/all_nodes_500[PREFER_LOWEST].png" /></td><td><image width=400 src="all_nodes_500[PREFER_LOWEST].png" /></td></tr></table>


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


## `black_hole[PREFER_LOWEST]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/black_hole[PREFER_LOWEST].png" /></td><td><image width=400 src="black_hole[PREFER_LOWEST].png" /></td></tr></table>


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


## `drastic_slowdown[PREFER_LOWEST]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/drastic_slowdown[PREFER_LOWEST].png" /></td><td><image width=400 src="drastic_slowdown[PREFER_LOWEST].png" /></td></tr></table>


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


## `fast_500s_then_revert[PREFER_LOWEST]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/fast_500s_then_revert[PREFER_LOWEST].png" /></td><td><image width=400 src="fast_500s_then_revert[PREFER_LOWEST].png" /></td></tr></table>


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


## `live_reloading[PREFER_LOWEST]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/live_reloading[PREFER_LOWEST].png" /></td><td><image width=400 src="live_reloading[PREFER_LOWEST].png" /></td></tr></table>


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


## `one_endpoint_dies_on_each_server[PREFER_LOWEST]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/one_endpoint_dies_on_each_server[PREFER_LOWEST].png" /></td><td><image width=400 src="one_endpoint_dies_on_each_server[PREFER_LOWEST].png" /></td></tr></table>


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


## `simplest_possible_case[PREFER_LOWEST]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/simplest_possible_case[PREFER_LOWEST].png" /></td><td><image width=400 src="simplest_possible_case[PREFER_LOWEST].png" /></td></tr></table>


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


## `slow_503s_then_revert[PREFER_LOWEST]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/slow_503s_then_revert[PREFER_LOWEST].png" /></td><td><image width=400 src="slow_503s_then_revert[PREFER_LOWEST].png" /></td></tr></table>


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


## `slowdown_and_error_thresholds[PREFER_LOWEST]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/slowdown_and_error_thresholds[PREFER_LOWEST].png" /></td><td><image width=400 src="slowdown_and_error_thresholds[PREFER_LOWEST].png" /></td></tr></table>


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


## `uncommon_flakes[PREFER_LOWEST]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/uncommon_flakes[PREFER_LOWEST].png" /></td><td><image width=400 src="uncommon_flakes[PREFER_LOWEST].png" /></td></tr></table>


## `uncommon_flakes[UNLIMITED_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/uncommon_flakes[UNLIMITED_ROUND_ROBIN].png" /></td><td><image width=400 src="uncommon_flakes[UNLIMITED_ROUND_ROBIN].png" /></td></tr></table>


