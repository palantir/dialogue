# Report
<!-- Run SimulationTest to regenerate this report. -->
```
                                all_nodes_500[CONCURRENCY_LIMITER].txt:	success=50.0%	client_mean=PT0.6S         	server_cpu=PT2M           	client_received=200/200	server_resps=200	codes={200=100, 500=100}
                                    all_nodes_500[PIN_UNTIL_ERROR].txt:	success=50.0%	client_mean=PT0.6S         	server_cpu=PT2M           	client_received=200/200	server_resps=200	codes={200=100, 500=100}
                                        all_nodes_500[ROUND_ROBIN].txt:	success=50.0%	client_mean=PT0.6S         	server_cpu=PT2M           	client_received=200/200	server_resps=200	codes={200=100, 500=100}
                                   black_hole[CONCURRENCY_LIMITER].txt:	success=65.0%	client_mean=PT0.6S         	server_cpu=PT1M18S        	client_received=130/200	server_resps=130	codes={200=130}
                                       black_hole[PIN_UNTIL_ERROR].txt:	success=30.5%	client_mean=PT0.6S         	server_cpu=PT36.6S        	client_received=61/200	server_resps=61	codes={200=61}
                                           black_hole[ROUND_ROBIN].txt:	success=65.0%	client_mean=PT0.6S         	server_cpu=PT1M18S        	client_received=130/200	server_resps=130	codes={200=130}
                             drastic_slowdown[CONCURRENCY_LIMITER].txt:	success=100.0%	client_mean=PT2.069939083S 	server_cpu=PT2H17M59.756333311S	client_received=4000/4000	server_resps=4000	codes={200=4000}
                                 drastic_slowdown[PIN_UNTIL_ERROR].txt:	success=100.0%	client_mean=PT2.053277999S 	server_cpu=PT2H16M53.111999959S	client_received=4000/4000	server_resps=4000	codes={200=4000}
                                     drastic_slowdown[ROUND_ROBIN].txt:	success=100.0%	client_mean=PT8.353421749S 	server_cpu=PT9H16M53.686999978S	client_received=4000/4000	server_resps=4000	codes={200=4000}
                        fast_500s_then_revert[CONCURRENCY_LIMITER].txt:	success=76.7%	client_mean=PT0.055463644S 	server_cpu=PT3M27.988666346S	client_received=3750/3750	server_resps=3750	codes={200=2876, 500=874}
                            fast_500s_then_revert[PIN_UNTIL_ERROR].txt:	success=99.7%	client_mean=PT0.080628266S 	server_cpu=PT5M2.355999997S	client_received=3750/3750	server_resps=3750	codes={200=3739, 500=11}
                                fast_500s_then_revert[ROUND_ROBIN].txt:	success=76.7%	client_mean=PT0.055463644S 	server_cpu=PT3M27.988666346S	client_received=3750/3750	server_resps=3750	codes={200=2876, 500=874}
                               live_reloading[CONCURRENCY_LIMITER].txt:	success=58.8%	client_mean=PT0.72295S     	server_cpu=PT4M49.18S     	client_received=400/400	server_resps=400	codes={200=235, 500=165}
                                   live_reloading[PIN_UNTIL_ERROR].txt:	success=89.3%	client_mean=PT0.946075S    	server_cpu=PT6M18.43S     	client_received=400/400	server_resps=400	codes={200=357, 500=43}
                                       live_reloading[ROUND_ROBIN].txt:	success=58.8%	client_mean=PT0.72295S     	server_cpu=PT4M49.18S     	client_received=400/400	server_resps=400	codes={200=235, 500=165}
             one_endpoint_dies_on_each_server[CONCURRENCY_LIMITER].txt:	success=65.3%	client_mean=PT0.6S         	server_cpu=PT5M6S         	client_received=510/510	server_resps=510	codes={200=333, 500=177}
                 one_endpoint_dies_on_each_server[PIN_UNTIL_ERROR].txt:	success=65.5%	client_mean=PT0.6S         	server_cpu=PT5M6S         	client_received=510/510	server_resps=510	codes={200=334, 500=176}
                     one_endpoint_dies_on_each_server[ROUND_ROBIN].txt:	success=65.3%	client_mean=PT0.6S         	server_cpu=PT5M6S         	client_received=510/510	server_resps=510	codes={200=333, 500=177}
                       simplest_possible_case[CONCURRENCY_LIMITER].txt:	success=100.0%	client_mean=PT0.7984S      	server_cpu=PT13M18.4S     	client_received=1000/1000	server_resps=1000	codes={200=1000}
                           simplest_possible_case[PIN_UNTIL_ERROR].txt:	success=100.0%	client_mean=PT1S           	server_cpu=PT16M40S       	client_received=1000/1000	server_resps=1000	codes={200=1000}
                               simplest_possible_case[ROUND_ROBIN].txt:	success=100.0%	client_mean=PT0.7984S      	server_cpu=PT13M18.4S     	client_received=1000/1000	server_resps=1000	codes={200=1000}
                        slow_503s_then_revert[CONCURRENCY_LIMITER].txt:	success=100.0%	client_mean=PT0.736112444S 	server_cpu=PT36M48.33733331S	client_received=3000/3000	server_resps=3416	codes={200=3000}
                            slow_503s_then_revert[PIN_UNTIL_ERROR].txt:	success=100.0%	client_mean=PT0.337337888S 	server_cpu=PT16M52.013666631S	client_received=3000/3000	server_resps=3197	codes={200=3000}
                                slow_503s_then_revert[ROUND_ROBIN].txt:	success=100.0%	client_mean=PT1.410522888S 	server_cpu=PT1H10M31.568666642S	client_received=3000/3000	server_resps=3810	codes={200=3000}
                slowdown_and_error_thresholds[CONCURRENCY_LIMITER].txt:	success=77.4%	client_mean=PT2.213039999S 	server_cpu=PT36M53.039999852S	client_received=1000/1000	server_resps=1000	codes={200=774, 500=226}
                    slowdown_and_error_thresholds[PIN_UNTIL_ERROR].txt:	success=39.2%	client_mean=PT3.284773333S 	server_cpu=PT54M44.773333296S	client_received=1000/1000	server_resps=1000	codes={200=392, 500=608}
                        slowdown_and_error_thresholds[ROUND_ROBIN].txt:	success=77.4%	client_mean=PT2.213039999S 	server_cpu=PT36M53.039999852S	client_received=1000/1000	server_resps=1000	codes={200=774, 500=226}
```



## all_nodes_500[CONCURRENCY_LIMITER].png
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/all_nodes_500[CONCURRENCY_LIMITER].png" /></td><td><image width=400 src="all_nodes_500[CONCURRENCY_LIMITER].png" /></td></tr></table>


## all_nodes_500[PIN_UNTIL_ERROR].png
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/all_nodes_500[PIN_UNTIL_ERROR].png" /></td><td><image width=400 src="all_nodes_500[PIN_UNTIL_ERROR].png" /></td></tr></table>


## all_nodes_500[ROUND_ROBIN].png
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/all_nodes_500[ROUND_ROBIN].png" /></td><td><image width=400 src="all_nodes_500[ROUND_ROBIN].png" /></td></tr></table>


## black_hole[CONCURRENCY_LIMITER].png
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/black_hole[CONCURRENCY_LIMITER].png" /></td><td><image width=400 src="black_hole[CONCURRENCY_LIMITER].png" /></td></tr></table>


## black_hole[PIN_UNTIL_ERROR].png
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/black_hole[PIN_UNTIL_ERROR].png" /></td><td><image width=400 src="black_hole[PIN_UNTIL_ERROR].png" /></td></tr></table>


## black_hole[ROUND_ROBIN].png
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/black_hole[ROUND_ROBIN].png" /></td><td><image width=400 src="black_hole[ROUND_ROBIN].png" /></td></tr></table>


## drastic_slowdown[CONCURRENCY_LIMITER].png
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/drastic_slowdown[CONCURRENCY_LIMITER].png" /></td><td><image width=400 src="drastic_slowdown[CONCURRENCY_LIMITER].png" /></td></tr></table>


## drastic_slowdown[PIN_UNTIL_ERROR].png
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/drastic_slowdown[PIN_UNTIL_ERROR].png" /></td><td><image width=400 src="drastic_slowdown[PIN_UNTIL_ERROR].png" /></td></tr></table>


## drastic_slowdown[ROUND_ROBIN].png
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/drastic_slowdown[ROUND_ROBIN].png" /></td><td><image width=400 src="drastic_slowdown[ROUND_ROBIN].png" /></td></tr></table>


## fast_500s_then_revert[CONCURRENCY_LIMITER].png
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/fast_500s_then_revert[CONCURRENCY_LIMITER].png" /></td><td><image width=400 src="fast_500s_then_revert[CONCURRENCY_LIMITER].png" /></td></tr></table>


## fast_500s_then_revert[PIN_UNTIL_ERROR].png
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/fast_500s_then_revert[PIN_UNTIL_ERROR].png" /></td><td><image width=400 src="fast_500s_then_revert[PIN_UNTIL_ERROR].png" /></td></tr></table>


## fast_500s_then_revert[ROUND_ROBIN].png
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/fast_500s_then_revert[ROUND_ROBIN].png" /></td><td><image width=400 src="fast_500s_then_revert[ROUND_ROBIN].png" /></td></tr></table>


## live_reloading[CONCURRENCY_LIMITER].png
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/live_reloading[CONCURRENCY_LIMITER].png" /></td><td><image width=400 src="live_reloading[CONCURRENCY_LIMITER].png" /></td></tr></table>


## live_reloading[PIN_UNTIL_ERROR].png
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/live_reloading[PIN_UNTIL_ERROR].png" /></td><td><image width=400 src="live_reloading[PIN_UNTIL_ERROR].png" /></td></tr></table>


## live_reloading[ROUND_ROBIN].png
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/live_reloading[ROUND_ROBIN].png" /></td><td><image width=400 src="live_reloading[ROUND_ROBIN].png" /></td></tr></table>


## one_endpoint_dies_on_each_server[CONCURRENCY_LIMITER].png
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/one_endpoint_dies_on_each_server[CONCURRENCY_LIMITER].png" /></td><td><image width=400 src="one_endpoint_dies_on_each_server[CONCURRENCY_LIMITER].png" /></td></tr></table>


## one_endpoint_dies_on_each_server[PIN_UNTIL_ERROR].png
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/one_endpoint_dies_on_each_server[PIN_UNTIL_ERROR].png" /></td><td><image width=400 src="one_endpoint_dies_on_each_server[PIN_UNTIL_ERROR].png" /></td></tr></table>


## one_endpoint_dies_on_each_server[ROUND_ROBIN].png
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/one_endpoint_dies_on_each_server[ROUND_ROBIN].png" /></td><td><image width=400 src="one_endpoint_dies_on_each_server[ROUND_ROBIN].png" /></td></tr></table>


## simplest_possible_case[CONCURRENCY_LIMITER].png
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/simplest_possible_case[CONCURRENCY_LIMITER].png" /></td><td><image width=400 src="simplest_possible_case[CONCURRENCY_LIMITER].png" /></td></tr></table>


## simplest_possible_case[PIN_UNTIL_ERROR].png
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/simplest_possible_case[PIN_UNTIL_ERROR].png" /></td><td><image width=400 src="simplest_possible_case[PIN_UNTIL_ERROR].png" /></td></tr></table>


## simplest_possible_case[ROUND_ROBIN].png
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/simplest_possible_case[ROUND_ROBIN].png" /></td><td><image width=400 src="simplest_possible_case[ROUND_ROBIN].png" /></td></tr></table>


## slow_503s_then_revert[CONCURRENCY_LIMITER].png
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/slow_503s_then_revert[CONCURRENCY_LIMITER].png" /></td><td><image width=400 src="slow_503s_then_revert[CONCURRENCY_LIMITER].png" /></td></tr></table>


## slow_503s_then_revert[PIN_UNTIL_ERROR].png
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/slow_503s_then_revert[PIN_UNTIL_ERROR].png" /></td><td><image width=400 src="slow_503s_then_revert[PIN_UNTIL_ERROR].png" /></td></tr></table>


## slow_503s_then_revert[ROUND_ROBIN].png
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/slow_503s_then_revert[ROUND_ROBIN].png" /></td><td><image width=400 src="slow_503s_then_revert[ROUND_ROBIN].png" /></td></tr></table>


## slowdown_and_error_thresholds[CONCURRENCY_LIMITER].png
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/slowdown_and_error_thresholds[CONCURRENCY_LIMITER].png" /></td><td><image width=400 src="slowdown_and_error_thresholds[CONCURRENCY_LIMITER].png" /></td></tr></table>


## slowdown_and_error_thresholds[PIN_UNTIL_ERROR].png
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/slowdown_and_error_thresholds[PIN_UNTIL_ERROR].png" /></td><td><image width=400 src="slowdown_and_error_thresholds[PIN_UNTIL_ERROR].png" /></td></tr></table>


## slowdown_and_error_thresholds[ROUND_ROBIN].png
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/slowdown_and_error_thresholds[ROUND_ROBIN].png" /></td><td><image width=400 src="slowdown_and_error_thresholds[ROUND_ROBIN].png" /></td></tr></table>


