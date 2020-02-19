# Report
<!-- Run SimulationTest to regenerate this report. -->
```
          all_nodes_500[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN].txt:	success=78.1%	client_mean=PT2.54955S     	server_cpu=PT20M          	client_received=2000/2000	server_resps=2000	codes={200=1562, 500=438}
                all_nodes_500[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=50.0%	client_mean=PT0.6S         	server_cpu=PT20M          	client_received=2000/2000	server_resps=2000	codes={200=1000, 500=1000}
                    all_nodes_500[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=50.0%	client_mean=PT0.6S         	server_cpu=PT20M          	client_received=2000/2000	server_resps=2000	codes={200=1000, 500=1000}
                              all_nodes_500[UNLIMITED_ROUND_ROBIN].txt:	success=50.0%	client_mean=PT0.6S         	server_cpu=PT20M          	client_received=2000/2000	server_resps=2000	codes={200=1000, 500=1000}
             black_hole[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN].txt:	success=89.9%	client_mean=PT0.600086254S 	server_cpu=PT17M58.2S     	client_received=1797/2000	server_resps=1797	codes={200=1797}
                   black_hole[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=88.7%	client_mean=PT0.600191765S 	server_cpu=PT17M43.8S     	client_received=1773/2000	server_resps=1773	codes={200=1773}
                       black_hole[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=89.9%	client_mean=PT0.600086254S 	server_cpu=PT17M58.2S     	client_received=1797/2000	server_resps=1797	codes={200=1797}
                                 black_hole[UNLIMITED_ROUND_ROBIN].txt:	success=65.0%	client_mean=PT0.6S         	server_cpu=PT12M59.4S     	client_received=1299/2000	server_resps=1299	codes={200=1299}
       drastic_slowdown[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN].txt:	success=100.0%	client_mean=PT2.069939083S 	server_cpu=PT2H17M59.756333311S	client_received=4000/4000	server_resps=4000	codes={200=4000}
             drastic_slowdown[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=100.0%	client_mean=PT2.053277999S 	server_cpu=PT2H16M53.111999959S	client_received=4000/4000	server_resps=4000	codes={200=4000}
                 drastic_slowdown[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=100.0%	client_mean=PT2.069939083S 	server_cpu=PT2H17M59.756333311S	client_received=4000/4000	server_resps=4000	codes={200=4000}
                           drastic_slowdown[UNLIMITED_ROUND_ROBIN].txt:	success=100.0%	client_mean=PT8.353421749S 	server_cpu=PT9H16M53.686999978S	client_received=4000/4000	server_resps=4000	codes={200=4000}
  fast_500s_then_revert[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN].txt:	success=98.1%	client_mean=PT0.073222888S 	server_cpu=PT4M34.585833294S	client_received=3750/3750	server_resps=3750	codes={200=3679, 500=71}
        fast_500s_then_revert[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=99.7%	client_mean=PT0.080628266S 	server_cpu=PT5M2.355999997S	client_received=3750/3750	server_resps=3750	codes={200=3739, 500=11}
            fast_500s_then_revert[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=76.7%	client_mean=PT0.055463644S 	server_cpu=PT3M27.988666346S	client_received=3750/3750	server_resps=3750	codes={200=2876, 500=874}
                      fast_500s_then_revert[UNLIMITED_ROUND_ROBIN].txt:	success=76.7%	client_mean=PT0.055463644S 	server_cpu=PT3M27.988666346S	client_received=3750/3750	server_resps=3750	codes={200=2876, 500=874}
         live_reloading[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN].txt:	success=79.5%	client_mean=PT4.540588S    	server_cpu=PT1H53M52.44S  	client_received=2500/2500	server_resps=2500	codes={200=1988, 500=512}
               live_reloading[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=58.9%	client_mean=PT3.5763136S   	server_cpu=PT1H58M42.9S   	client_received=2500/2500	server_resps=2500	codes={200=1473, 500=1027}
                   live_reloading[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=58.6%	client_mean=PT3.5376608S   	server_cpu=PT1H58M19S     	client_received=2500/2500	server_resps=2500	codes={200=1466, 500=1034}
                             live_reloading[UNLIMITED_ROUND_ROBIN].txt:	success=58.4%	client_mean=PT2.8396S      	server_cpu=PT1H58M19S     	client_received=2500/2500	server_resps=2500	codes={200=1461, 500=1039}
one_endpoint_dies_on_each_server[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN].txt:	success=87.9%	client_mean=PT0.6S         	server_cpu=PT25M          	client_received=2500/2500	server_resps=2500	codes={200=2197, 500=303}
one_endpoint_dies_on_each_server[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=63.8%	client_mean=PT0.6S         	server_cpu=PT25M          	client_received=2500/2500	server_resps=2500	codes={200=1596, 500=904}
 one_endpoint_dies_on_each_server[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=65.5%	client_mean=PT0.6S         	server_cpu=PT25M          	client_received=2500/2500	server_resps=2500	codes={200=1638, 500=862}
           one_endpoint_dies_on_each_server[UNLIMITED_ROUND_ROBIN].txt:	success=65.5%	client_mean=PT0.6S         	server_cpu=PT25M          	client_received=2500/2500	server_resps=2500	codes={200=1638, 500=862}
 simplest_possible_case[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN].txt:	success=100.0%	client_mean=PT0.799939393S 	server_cpu=PT2H55M59.2S   	client_received=13200/13200	server_resps=13200	codes={200=13200}
       simplest_possible_case[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=100.0%	client_mean=PT0.998696969S 	server_cpu=PT3H39M42.8S   	client_received=13200/13200	server_resps=13200	codes={200=13200}
           simplest_possible_case[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=100.0%	client_mean=PT0.799939393S 	server_cpu=PT2H55M59.2S   	client_received=13200/13200	server_resps=13200	codes={200=13200}
                     simplest_possible_case[UNLIMITED_ROUND_ROBIN].txt:	success=100.0%	client_mean=PT0.799939393S 	server_cpu=PT2H55M59.2S   	client_received=13200/13200	server_resps=13200	codes={200=13200}
  slow_503s_then_revert[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN].txt:	success=100.0%	client_mean=PT0.302478666S 	server_cpu=PT15M7.435999978S	client_received=3000/3000	server_resps=3177	codes={200=3000}
        slow_503s_then_revert[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=100.0%	client_mean=PT0.337337888S 	server_cpu=PT16M52.013666631S	client_received=3000/3000	server_resps=3197	codes={200=3000}
            slow_503s_then_revert[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=100.0%	client_mean=PT0.736112444S 	server_cpu=PT36M48.33733331S	client_received=3000/3000	server_resps=3416	codes={200=3000}
                      slow_503s_then_revert[UNLIMITED_ROUND_ROBIN].txt:	success=100.0%	client_mean=PT1.410522888S 	server_cpu=PT1H10M31.568666642S	client_received=3000/3000	server_resps=3810	codes={200=3000}
slowdown_and_error_thresholds[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN].txt:	success=23.0%	client_mean=PT1M34.851194133S	server_cpu=PT10H14M8.139999893S	client_received=10000/10000	server_resps=10000	codes={200=2296, 500=7704}
slowdown_and_error_thresholds[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=1.8%	client_mean=PT20.068609533S	server_cpu=PT10H35M7.200666646S	client_received=10000/10000	server_resps=10000	codes={200=176, 500=9824}
    slowdown_and_error_thresholds[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=1.2%	client_mean=PT16.859225466S	server_cpu=PT10H30M49.207333306S	client_received=10000/10000	server_resps=10000	codes={200=120, 500=9880}
              slowdown_and_error_thresholds[UNLIMITED_ROUND_ROBIN].txt:	success=1.2%	client_mean=PT3.974119999S 	server_cpu=PT11H2M21.19999998S	client_received=10000/10000	server_resps=10000	codes={200=120, 500=9880}
```



## `all_nodes_500[CONCURRENCY_LIMITER]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/all_nodes_500[CONCURRENCY_LIMITER].png" /></td><td><image width=400 src="all_nodes_500[CONCURRENCY_LIMITER].png" /></td></tr></table>


## `all_nodes_500[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/all_nodes_500[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN].png" /></td><td><image width=400 src="all_nodes_500[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN].png" /></td></tr></table>


## `all_nodes_500[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/all_nodes_500[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td><td><image width=400 src="all_nodes_500[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td></tr></table>


## `all_nodes_500[CONCURRENCY_LIMITER_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/all_nodes_500[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td><td><image width=400 src="all_nodes_500[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td></tr></table>


## `all_nodes_500[PIN_UNTIL_ERROR]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/all_nodes_500[PIN_UNTIL_ERROR].png" /></td><td><image width=400 src="all_nodes_500[PIN_UNTIL_ERROR].png" /></td></tr></table>


## `all_nodes_500[ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/all_nodes_500[ROUND_ROBIN].png" /></td><td><image width=400 src="all_nodes_500[ROUND_ROBIN].png" /></td></tr></table>


## `all_nodes_500[UNLIMITED_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/all_nodes_500[UNLIMITED_ROUND_ROBIN].png" /></td><td><image width=400 src="all_nodes_500[UNLIMITED_ROUND_ROBIN].png" /></td></tr></table>


## `black_hole[CONCURRENCY_LIMITER]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/black_hole[CONCURRENCY_LIMITER].png" /></td><td><image width=400 src="black_hole[CONCURRENCY_LIMITER].png" /></td></tr></table>


## `black_hole[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/black_hole[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN].png" /></td><td><image width=400 src="black_hole[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN].png" /></td></tr></table>


## `black_hole[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/black_hole[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td><td><image width=400 src="black_hole[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td></tr></table>


## `black_hole[CONCURRENCY_LIMITER_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/black_hole[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td><td><image width=400 src="black_hole[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td></tr></table>


## `black_hole[PIN_UNTIL_ERROR]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/black_hole[PIN_UNTIL_ERROR].png" /></td><td><image width=400 src="black_hole[PIN_UNTIL_ERROR].png" /></td></tr></table>


## `black_hole[ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/black_hole[ROUND_ROBIN].png" /></td><td><image width=400 src="black_hole[ROUND_ROBIN].png" /></td></tr></table>


## `black_hole[UNLIMITED_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/black_hole[UNLIMITED_ROUND_ROBIN].png" /></td><td><image width=400 src="black_hole[UNLIMITED_ROUND_ROBIN].png" /></td></tr></table>


## `drastic_slowdown[CONCURRENCY_LIMITER]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/drastic_slowdown[CONCURRENCY_LIMITER].png" /></td><td><image width=400 src="drastic_slowdown[CONCURRENCY_LIMITER].png" /></td></tr></table>


## `drastic_slowdown[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/drastic_slowdown[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN].png" /></td><td><image width=400 src="drastic_slowdown[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN].png" /></td></tr></table>


## `drastic_slowdown[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/drastic_slowdown[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td><td><image width=400 src="drastic_slowdown[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td></tr></table>


## `drastic_slowdown[CONCURRENCY_LIMITER_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/drastic_slowdown[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td><td><image width=400 src="drastic_slowdown[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td></tr></table>


## `drastic_slowdown[PIN_UNTIL_ERROR]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/drastic_slowdown[PIN_UNTIL_ERROR].png" /></td><td><image width=400 src="drastic_slowdown[PIN_UNTIL_ERROR].png" /></td></tr></table>


## `drastic_slowdown[ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/drastic_slowdown[ROUND_ROBIN].png" /></td><td><image width=400 src="drastic_slowdown[ROUND_ROBIN].png" /></td></tr></table>


## `drastic_slowdown[UNLIMITED_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/drastic_slowdown[UNLIMITED_ROUND_ROBIN].png" /></td><td><image width=400 src="drastic_slowdown[UNLIMITED_ROUND_ROBIN].png" /></td></tr></table>


## `fast_500s_then_revert[CONCURRENCY_LIMITER]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/fast_500s_then_revert[CONCURRENCY_LIMITER].png" /></td><td><image width=400 src="fast_500s_then_revert[CONCURRENCY_LIMITER].png" /></td></tr></table>


## `fast_500s_then_revert[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/fast_500s_then_revert[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN].png" /></td><td><image width=400 src="fast_500s_then_revert[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN].png" /></td></tr></table>


## `fast_500s_then_revert[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/fast_500s_then_revert[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td><td><image width=400 src="fast_500s_then_revert[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td></tr></table>


## `fast_500s_then_revert[CONCURRENCY_LIMITER_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/fast_500s_then_revert[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td><td><image width=400 src="fast_500s_then_revert[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td></tr></table>


## `fast_500s_then_revert[PIN_UNTIL_ERROR]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/fast_500s_then_revert[PIN_UNTIL_ERROR].png" /></td><td><image width=400 src="fast_500s_then_revert[PIN_UNTIL_ERROR].png" /></td></tr></table>


## `fast_500s_then_revert[ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/fast_500s_then_revert[ROUND_ROBIN].png" /></td><td><image width=400 src="fast_500s_then_revert[ROUND_ROBIN].png" /></td></tr></table>


## `fast_500s_then_revert[UNLIMITED_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/fast_500s_then_revert[UNLIMITED_ROUND_ROBIN].png" /></td><td><image width=400 src="fast_500s_then_revert[UNLIMITED_ROUND_ROBIN].png" /></td></tr></table>


## `live_reloading[CONCURRENCY_LIMITER]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/live_reloading[CONCURRENCY_LIMITER].png" /></td><td><image width=400 src="live_reloading[CONCURRENCY_LIMITER].png" /></td></tr></table>


## `live_reloading[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/live_reloading[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN].png" /></td><td><image width=400 src="live_reloading[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN].png" /></td></tr></table>


## `live_reloading[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/live_reloading[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td><td><image width=400 src="live_reloading[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td></tr></table>


## `live_reloading[CONCURRENCY_LIMITER_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/live_reloading[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td><td><image width=400 src="live_reloading[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td></tr></table>


## `live_reloading[PIN_UNTIL_ERROR]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/live_reloading[PIN_UNTIL_ERROR].png" /></td><td><image width=400 src="live_reloading[PIN_UNTIL_ERROR].png" /></td></tr></table>


## `live_reloading[ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/live_reloading[ROUND_ROBIN].png" /></td><td><image width=400 src="live_reloading[ROUND_ROBIN].png" /></td></tr></table>


## `live_reloading[UNLIMITED_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/live_reloading[UNLIMITED_ROUND_ROBIN].png" /></td><td><image width=400 src="live_reloading[UNLIMITED_ROUND_ROBIN].png" /></td></tr></table>


## `one_endpoint_dies_on_each_server[CONCURRENCY_LIMITER]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/one_endpoint_dies_on_each_server[CONCURRENCY_LIMITER].png" /></td><td><image width=400 src="one_endpoint_dies_on_each_server[CONCURRENCY_LIMITER].png" /></td></tr></table>


## `one_endpoint_dies_on_each_server[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/one_endpoint_dies_on_each_server[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN].png" /></td><td><image width=400 src="one_endpoint_dies_on_each_server[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN].png" /></td></tr></table>


## `one_endpoint_dies_on_each_server[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/one_endpoint_dies_on_each_server[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td><td><image width=400 src="one_endpoint_dies_on_each_server[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td></tr></table>


## `one_endpoint_dies_on_each_server[CONCURRENCY_LIMITER_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/one_endpoint_dies_on_each_server[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td><td><image width=400 src="one_endpoint_dies_on_each_server[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td></tr></table>


## `one_endpoint_dies_on_each_server[PIN_UNTIL_ERROR]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/one_endpoint_dies_on_each_server[PIN_UNTIL_ERROR].png" /></td><td><image width=400 src="one_endpoint_dies_on_each_server[PIN_UNTIL_ERROR].png" /></td></tr></table>


## `one_endpoint_dies_on_each_server[ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/one_endpoint_dies_on_each_server[ROUND_ROBIN].png" /></td><td><image width=400 src="one_endpoint_dies_on_each_server[ROUND_ROBIN].png" /></td></tr></table>


## `one_endpoint_dies_on_each_server[UNLIMITED_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/one_endpoint_dies_on_each_server[UNLIMITED_ROUND_ROBIN].png" /></td><td><image width=400 src="one_endpoint_dies_on_each_server[UNLIMITED_ROUND_ROBIN].png" /></td></tr></table>


## `simplest_possible_case[CONCURRENCY_LIMITER]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/simplest_possible_case[CONCURRENCY_LIMITER].png" /></td><td><image width=400 src="simplest_possible_case[CONCURRENCY_LIMITER].png" /></td></tr></table>


## `simplest_possible_case[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/simplest_possible_case[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN].png" /></td><td><image width=400 src="simplest_possible_case[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN].png" /></td></tr></table>


## `simplest_possible_case[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/simplest_possible_case[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td><td><image width=400 src="simplest_possible_case[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td></tr></table>


## `simplest_possible_case[CONCURRENCY_LIMITER_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/simplest_possible_case[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td><td><image width=400 src="simplest_possible_case[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td></tr></table>


## `simplest_possible_case[PIN_UNTIL_ERROR]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/simplest_possible_case[PIN_UNTIL_ERROR].png" /></td><td><image width=400 src="simplest_possible_case[PIN_UNTIL_ERROR].png" /></td></tr></table>


## `simplest_possible_case[ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/simplest_possible_case[ROUND_ROBIN].png" /></td><td><image width=400 src="simplest_possible_case[ROUND_ROBIN].png" /></td></tr></table>


## `simplest_possible_case[UNLIMITED_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/simplest_possible_case[UNLIMITED_ROUND_ROBIN].png" /></td><td><image width=400 src="simplest_possible_case[UNLIMITED_ROUND_ROBIN].png" /></td></tr></table>


## `slow_503s_then_revert[CONCURRENCY_LIMITER]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/slow_503s_then_revert[CONCURRENCY_LIMITER].png" /></td><td><image width=400 src="slow_503s_then_revert[CONCURRENCY_LIMITER].png" /></td></tr></table>


## `slow_503s_then_revert[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/slow_503s_then_revert[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN].png" /></td><td><image width=400 src="slow_503s_then_revert[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN].png" /></td></tr></table>


## `slow_503s_then_revert[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/slow_503s_then_revert[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td><td><image width=400 src="slow_503s_then_revert[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td></tr></table>


## `slow_503s_then_revert[CONCURRENCY_LIMITER_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/slow_503s_then_revert[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td><td><image width=400 src="slow_503s_then_revert[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td></tr></table>


## `slow_503s_then_revert[PIN_UNTIL_ERROR]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/slow_503s_then_revert[PIN_UNTIL_ERROR].png" /></td><td><image width=400 src="slow_503s_then_revert[PIN_UNTIL_ERROR].png" /></td></tr></table>


## `slow_503s_then_revert[ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/slow_503s_then_revert[ROUND_ROBIN].png" /></td><td><image width=400 src="slow_503s_then_revert[ROUND_ROBIN].png" /></td></tr></table>


## `slow_503s_then_revert[UNLIMITED_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/slow_503s_then_revert[UNLIMITED_ROUND_ROBIN].png" /></td><td><image width=400 src="slow_503s_then_revert[UNLIMITED_ROUND_ROBIN].png" /></td></tr></table>


## `slowdown_and_error_thresholds[CONCURRENCY_LIMITER]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/slowdown_and_error_thresholds[CONCURRENCY_LIMITER].png" /></td><td><image width=400 src="slowdown_and_error_thresholds[CONCURRENCY_LIMITER].png" /></td></tr></table>


## `slowdown_and_error_thresholds[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/slowdown_and_error_thresholds[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN].png" /></td><td><image width=400 src="slowdown_and_error_thresholds[CONCURRENCY_LIMITER_BLACKLIST_ROUND_ROBIN].png" /></td></tr></table>


## `slowdown_and_error_thresholds[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/slowdown_and_error_thresholds[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td><td><image width=400 src="slowdown_and_error_thresholds[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td></tr></table>


## `slowdown_and_error_thresholds[CONCURRENCY_LIMITER_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/slowdown_and_error_thresholds[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td><td><image width=400 src="slowdown_and_error_thresholds[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td></tr></table>


## `slowdown_and_error_thresholds[PIN_UNTIL_ERROR]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/slowdown_and_error_thresholds[PIN_UNTIL_ERROR].png" /></td><td><image width=400 src="slowdown_and_error_thresholds[PIN_UNTIL_ERROR].png" /></td></tr></table>


## `slowdown_and_error_thresholds[ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/slowdown_and_error_thresholds[ROUND_ROBIN].png" /></td><td><image width=400 src="slowdown_and_error_thresholds[ROUND_ROBIN].png" /></td></tr></table>


## `slowdown_and_error_thresholds[UNLIMITED_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/slowdown_and_error_thresholds[UNLIMITED_ROUND_ROBIN].png" /></td><td><image width=400 src="slowdown_and_error_thresholds[UNLIMITED_ROUND_ROBIN].png" /></td></tr></table>


