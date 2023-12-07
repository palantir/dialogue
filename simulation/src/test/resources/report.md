# Report
<!-- Run SimulationTest to regenerate this report. -->
```
                all_nodes_500[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=73.9%	client_mean=PT4.24575S     	server_cpu=PT20M          	client_received=2000/2000	server_resps=2000	codes={200=1478, 500=522}
client=0 endpoint	client_mean=PT3.548823529S 
client=1 endpoint	client_mean=PT3.582553191S 
client=2 endpoint	client_mean=PT3.681930693S 
client=3 endpoint	client_mean=PT4.455515463S 
client=4 endpoint	client_mean=PT3.855801104S 
client=5 endpoint	client_mean=PT4.737788018S 
client=6 endpoint	client_mean=PT3.922931937S 
client=7 endpoint	client_mean=PT4.824258373S 
client=8 endpoint	client_mean=PT4.446666666S 
client=9 endpoint	client_mean=PT5.126511627S 

                    all_nodes_500[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=74.1%	client_mean=PT3.347395S    	server_cpu=PT20M          	client_received=2000/2000	server_resps=2000	codes={200=1481, 500=519}
client=0 endpoint	client_mean=PT2.841550802S 
client=1 endpoint	client_mean=PT2.532287234S 
client=2 endpoint	client_mean=PT2.811881188S 
client=3 endpoint	client_mean=PT3.716391752S 
client=4 endpoint	client_mean=PT3.194640883S 
client=5 endpoint	client_mean=PT3.73751152S  
client=6 endpoint	client_mean=PT2.975706806S 
client=7 endpoint	client_mean=PT3.845215311S 
client=8 endpoint	client_mean=PT3.516388888S 
client=9 endpoint	client_mean=PT4.081627906S 

                              all_nodes_500[UNLIMITED_ROUND_ROBIN].txt:	success=50.0%	client_mean=PT0.6S         	server_cpu=PT20M          	client_received=2000/2000	server_resps=2000	codes={200=1000, 500=1000}
client=0 endpoint	client_mean=PT0.6S         
client=1 endpoint	client_mean=PT0.6S         
client=2 endpoint	client_mean=PT0.6S         
client=3 endpoint	client_mean=PT0.6S         
client=4 endpoint	client_mean=PT0.6S         
client=5 endpoint	client_mean=PT0.6S         
client=6 endpoint	client_mean=PT0.6S         
client=7 endpoint	client_mean=PT0.6S         
client=8 endpoint	client_mean=PT0.6S         
client=9 endpoint	client_mean=PT0.6S         

                   black_hole[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=59.2%	client_mean=PT0.600870667S 	server_cpu=PT11M49.8S     	client_received=1183/2000	server_resps=1183	codes={200=1183}
client=0 endpoint	client_mean=PT0.6S         
client=1 endpoint	client_mean=PT0.600398936S 
client=2 endpoint	client_mean=PT0.6S         
client=3 endpoint	client_mean=PT0.6S         
client=4 endpoint	client_mean=PT0.6S         
client=5 endpoint	client_mean=PT0.601267281S 
client=6 endpoint	client_mean=PT0.6S         
client=7 endpoint	client_mean=PT0.6S         
client=8 endpoint	client_mean=PT0.602129629S 
client=9 endpoint	client_mean=PT0.603333333S 

                       black_hole[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=91.5%	client_mean=PT0.600005464S 	server_cpu=PT18M18S       	client_received=1830/2000	server_resps=1830	codes={200=1830}
client=0 endpoint	client_mean=PT0.6S         
client=1 endpoint	client_mean=PT0.6S         
client=2 endpoint	client_mean=PT0.6S         
client=3 endpoint	client_mean=PT0.6S         
client=4 endpoint	client_mean=PT0.6S         
client=5 endpoint	client_mean=PT0.6S         
client=6 endpoint	client_mean=PT0.6S         
client=7 endpoint	client_mean=PT0.6S         
client=8 endpoint	client_mean=PT0.60005102S  
client=9 endpoint	client_mean=PT0.6S         

                                 black_hole[UNLIMITED_ROUND_ROBIN].txt:	success=91.4%	client_mean=PT0.6S         	server_cpu=PT18M16.8S     	client_received=1828/2000	server_resps=1828	codes={200=1828}
client=0 endpoint	client_mean=PT0.6S         
client=1 endpoint	client_mean=PT0.6S         
client=2 endpoint	client_mean=PT0.6S         
client=3 endpoint	client_mean=PT0.6S         
client=4 endpoint	client_mean=PT0.6S         
client=5 endpoint	client_mean=PT0.6S         
client=6 endpoint	client_mean=PT0.6S         
client=7 endpoint	client_mean=PT0.6S         
client=8 endpoint	client_mean=PT0.6S         
client=9 endpoint	client_mean=PT0.6S         

             drastic_slowdown[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=100.0%	client_mean=PT3.120674249S 	server_cpu=PT41M7.682333314S	client_received=4000/4000	server_resps=4000	codes={200=4000}
client=0 endpoint	client_mean=PT0.068773437S 
client=1 endpoint	client_mean=PT7.862170666S 
client=2 endpoint	client_mean=PT8.121897058S 
client=3 endpoint	client_mean=PT0.068805486S 
client=4 endpoint	client_mean=PT0.06881491S  
client=5 endpoint	client_mean=PT7.420232445S 
client=6 endpoint	client_mean=PT0.068880407S 
client=7 endpoint	client_mean=PT0.068901265S 
client=8 endpoint	client_mean=PT7.331328431S 
client=9 endpoint	client_mean=PT0.068792626S 

                 drastic_slowdown[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=100.0%	client_mean=PT0.251969999S 	server_cpu=PT16M47.879999984S	client_received=4000/4000	server_resps=4000	codes={200=4000}
client=0 endpoint	client_mean=PT0.257360243S 
client=1 endpoint	client_mean=PT0.272277333S 
client=2 endpoint	client_mean=PT0.24984232S  
client=3 endpoint	client_mean=PT0.252248545S 
client=4 endpoint	client_mean=PT0.225579263S 
client=5 endpoint	client_mean=PT0.242807909S 
client=6 endpoint	client_mean=PT0.255286683S 
client=7 endpoint	client_mean=PT0.244582278S 
client=8 endpoint	client_mean=PT0.293598856S 
client=9 endpoint	client_mean=PT0.228355606S 

                           drastic_slowdown[UNLIMITED_ROUND_ROBIN].txt:	success=100.0%	client_mean=PT0.251969999S 	server_cpu=PT16M47.879999984S	client_received=4000/4000	server_resps=4000	codes={200=4000}
client=0 endpoint	client_mean=PT0.257360243S 
client=1 endpoint	client_mean=PT0.272277333S 
client=2 endpoint	client_mean=PT0.24984232S  
client=3 endpoint	client_mean=PT0.252248545S 
client=4 endpoint	client_mean=PT0.225579263S 
client=5 endpoint	client_mean=PT0.242807909S 
client=6 endpoint	client_mean=PT0.255286683S 
client=7 endpoint	client_mean=PT0.244582278S 
client=8 endpoint	client_mean=PT0.293598856S 
client=9 endpoint	client_mean=PT0.228355606S 

        fast_400s_then_revert[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=82.2%	client_mean=PT0.1022S      	server_cpu=PT10M13.2S     	client_received=6000/6000	server_resps=6000	codes={200=4932, 400=1068}
client=0 endpoint	client_mean=PT0.12S        
client=1 endpoint	client_mean=PT0.078003442S 
client=2 endpoint	client_mean=PT0.074707792S 
client=3 endpoint	client_mean=PT0.12S        
client=4 endpoint	client_mean=PT0.12S        
client=5 endpoint	client_mean=PT0.075482815S 
client=6 endpoint	client_mean=PT0.12S        
client=7 endpoint	client_mean=PT0.12S        
client=8 endpoint	client_mean=PT0.075464926S 
client=9 endpoint	client_mean=PT0.12S        

            fast_400s_then_revert[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=93.6%	client_mean=PT0.113583333S 	server_cpu=PT11M21.5S     	client_received=6000/6000	server_resps=6000	codes={200=5615, 400=385}
client=0 endpoint	client_mean=PT0.113238434S 
client=1 endpoint	client_mean=PT0.113287435S 
client=2 endpoint	client_mean=PT0.113668831S 
client=3 endpoint	client_mean=PT0.113760262S 
client=4 endpoint	client_mean=PT0.113877551S 
client=5 endpoint	client_mean=PT0.113617021S 
client=6 endpoint	client_mean=PT0.113019197S 
client=7 endpoint	client_mean=PT0.113613445S 
client=8 endpoint	client_mean=PT0.113800978S 
client=9 endpoint	client_mean=PT0.11386503S  

                      fast_400s_then_revert[UNLIMITED_ROUND_ROBIN].txt:	success=93.6%	client_mean=PT0.113583333S 	server_cpu=PT11M21.5S     	client_received=6000/6000	server_resps=6000	codes={200=5615, 400=385}
client=0 endpoint	client_mean=PT0.113238434S 
client=1 endpoint	client_mean=PT0.113287435S 
client=2 endpoint	client_mean=PT0.113668831S 
client=3 endpoint	client_mean=PT0.113760262S 
client=4 endpoint	client_mean=PT0.113877551S 
client=5 endpoint	client_mean=PT0.113617021S 
client=6 endpoint	client_mean=PT0.113019197S 
client=7 endpoint	client_mean=PT0.113613445S 
client=8 endpoint	client_mean=PT0.113800978S 
client=9 endpoint	client_mean=PT0.11386503S  

        fast_503s_then_revert[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=100.0%	client_mean=PT0.12S        	server_cpu=PT1H30M0.00000004S	client_received=45000/45000	server_resps=45004	codes={200=45000}
client=0 endpoint	client_mean=PT0.12S        
client=1 endpoint	client_mean=PT0.12S        
client=2 endpoint	client_mean=PT0.12S        
client=3 endpoint	client_mean=PT0.12S        
client=4 endpoint	client_mean=PT0.12S        
client=5 endpoint	client_mean=PT0.12S        
client=6 endpoint	client_mean=PT0.12S        
client=7 endpoint	client_mean=PT0.12S        
client=8 endpoint	client_mean=PT0.12S        
client=9 endpoint	client_mean=PT0.12S        

            fast_503s_then_revert[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=100.0%	client_mean=PT0.12S        	server_cpu=PT1H30M0.0000004S	client_received=45000/45000	server_resps=45040	codes={200=45000}
client=0 endpoint	client_mean=PT0.12S        
client=1 endpoint	client_mean=PT0.12S        
client=2 endpoint	client_mean=PT0.12S        
client=3 endpoint	client_mean=PT0.12S        
client=4 endpoint	client_mean=PT0.12S        
client=5 endpoint	client_mean=PT0.12S        
client=6 endpoint	client_mean=PT0.12S        
client=7 endpoint	client_mean=PT0.12S        
client=8 endpoint	client_mean=PT0.12S        
client=9 endpoint	client_mean=PT0.12S        

                      fast_503s_then_revert[UNLIMITED_ROUND_ROBIN].txt:	success=100.0%	client_mean=PT0.12S        	server_cpu=PT1H30M0.0000004S	client_received=45000/45000	server_resps=45040	codes={200=45000}
client=0 endpoint	client_mean=PT0.12S        
client=1 endpoint	client_mean=PT0.12S        
client=2 endpoint	client_mean=PT0.12S        
client=3 endpoint	client_mean=PT0.12S        
client=4 endpoint	client_mean=PT0.12S        
client=5 endpoint	client_mean=PT0.12S        
client=6 endpoint	client_mean=PT0.12S        
client=7 endpoint	client_mean=PT0.12S        
client=8 endpoint	client_mean=PT0.12S        
client=9 endpoint	client_mean=PT0.12S        

               live_reloading[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=94.3%	client_mean=PT7.1919248S   	server_cpu=PT1H55M52.15S  	client_received=2500/2500	server_resps=2500	codes={200=2357, 500=143}
client=0 endpoint	client_mean=PT8.096331983S 
client=1 endpoint	client_mean=PT6.97608S     
client=2 endpoint	client_mean=PT6.698398437S 
client=3 endpoint	client_mean=PT6.83588S     
client=4 endpoint	client_mean=PT6.364604444S 
client=5 endpoint	client_mean=PT6.777665399S 
client=6 endpoint	client_mean=PT7.338040983S 
client=7 endpoint	client_mean=PT7.215703125S 
client=8 endpoint	client_mean=PT8.047661654S 
client=9 endpoint	client_mean=PT7.439171641S 

                   live_reloading[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=92.9%	client_mean=PT5.3514976S   	server_cpu=PT1H55M20.28S  	client_received=2500/2500	server_resps=2500	codes={200=2323, 500=177}
client=0 endpoint	client_mean=PT4.979327935S 
client=1 endpoint	client_mean=PT4.926755555S 
client=2 endpoint	client_mean=PT5.196460937S 
client=3 endpoint	client_mean=PT5.450104S    
client=4 endpoint	client_mean=PT5.099902222S 
client=5 endpoint	client_mean=PT5.200608365S 
client=6 endpoint	client_mean=PT5.458336065S 
client=7 endpoint	client_mean=PT5.871492187S 
client=8 endpoint	client_mean=PT5.202601503S 
client=9 endpoint	client_mean=PT6.020313432S 

                             live_reloading[UNLIMITED_ROUND_ROBIN].txt:	success=86.9%	client_mean=PT2.802124S    	server_cpu=PT1H56M45.31S  	client_received=2500/2500	server_resps=2500	codes={200=2173, 500=327}
client=0 endpoint	client_mean=PT2.801578947S 
client=1 endpoint	client_mean=PT2.842933333S 
client=2 endpoint	client_mean=PT2.831328125S 
client=3 endpoint	client_mean=PT2.79328S     
client=4 endpoint	client_mean=PT2.821155555S 
client=5 endpoint	client_mean=PT2.734562737S 
client=6 endpoint	client_mean=PT2.868401639S 
client=7 endpoint	client_mean=PT2.84015625S  
client=8 endpoint	client_mean=PT2.748721804S 
client=9 endpoint	client_mean=PT2.755373134S 

                one_big_spike[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=100.0%	client_mean=PT2.667606696S 	server_cpu=PT2M30S        	client_received=1000/1000	server_resps=1000	codes={200=1000}
client=0 endpoint	client_mean=PT2.667606696S 

                    one_big_spike[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=100.0%	client_mean=PT1.579726259S 	server_cpu=PT2M30S        	client_received=1000/1000	server_resps=1000	codes={200=1000}
client=0 endpoint	client_mean=PT1.579726259S 

                              one_big_spike[UNLIMITED_ROUND_ROBIN].txt:	success=93.8%	client_mean=PT1.310790631S 	server_cpu=PT10M53.4S     	client_received=1000/1000	server_resps=4356	codes={200=938, 429=62}
client=0 endpoint	client_mean=PT1.310790631S 

one_endpoint_dies_on_each_server[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=66.0%	client_mean=PT2.0950112S   	server_cpu=PT25M          	client_received=2500/2500	server_resps=2500	codes={200=1649, 500=851}
client=0 e1	client_mean=PT1.837701492S 
client=0 e2	client_mean=PT1.603259259S 
client=1 e1	client_mean=PT2.485967213S 
client=1 e2	client_mean=PT2.081661538S 
client=2 e1	client_mean=PT1.640336134S 
client=2 e2	client_mean=PT1.626137404S 
client=3 e1	client_mean=PT2.297536S    
client=3 e2	client_mean=PT1.980492307S 
client=4 e1	client_mean=PT2.988895522S 
client=4 e2	client_mean=PT3.099043478S 
client=5 e1	client_mean=PT1.742690265S 
client=5 e2	client_mean=PT1.407247863S 
client=6 e1	client_mean=PT1.958537313S 
client=6 e2	client_mean=PT2.245033333S 
client=7 e1	client_mean=PT1.650943396S 
client=7 e2	client_mean=PT2.003696969S 
client=8 e1	client_mean=PT2.32712605S  
client=8 e2	client_mean=PT2.703841269S 
client=9 e1	client_mean=PT2.112696969S 
client=9 e2	client_mean=PT1.798184615S 

 one_endpoint_dies_on_each_server[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=66.2%	client_mean=PT2.0278288S   	server_cpu=PT25M          	client_received=2500/2500	server_resps=2500	codes={200=1655, 500=845}
client=0 e1	client_mean=PT1.801791044S 
client=0 e2	client_mean=PT1.526444444S 
client=1 e1	client_mean=PT2.66862295S  
client=1 e2	client_mean=PT2.302061538S 
client=2 e1	client_mean=PT2.310554621S 
client=2 e2	client_mean=PT1.809343511S 
client=3 e1	client_mean=PT1.780192S    
client=3 e2	client_mean=PT2.00916923S  
client=4 e1	client_mean=PT2.002507462S 
client=4 e2	client_mean=PT1.895043478S 
client=5 e1	client_mean=PT1.810123893S 
client=5 e2	client_mean=PT1.99008547S  
client=6 e1	client_mean=PT2.318358208S 
client=6 e2	client_mean=PT2.169066666S 
client=7 e1	client_mean=PT1.735849056S 
client=7 e2	client_mean=PT1.211212121S 
client=8 e1	client_mean=PT2.685983193S 
client=8 e2	client_mean=PT2.616571428S 
client=9 e1	client_mean=PT1.950969696S 
client=9 e2	client_mean=PT1.952523076S 

           one_endpoint_dies_on_each_server[UNLIMITED_ROUND_ROBIN].txt:	success=65.1%	client_mean=PT0.6S         	server_cpu=PT25M          	client_received=2500/2500	server_resps=2500	codes={200=1628, 500=872}
client=0 e1	client_mean=PT0.6S         
client=0 e2	client_mean=PT0.6S         
client=1 e1	client_mean=PT0.6S         
client=1 e2	client_mean=PT0.6S         
client=2 e1	client_mean=PT0.6S         
client=2 e2	client_mean=PT0.6S         
client=3 e1	client_mean=PT0.6S         
client=3 e2	client_mean=PT0.6S         
client=4 e1	client_mean=PT0.6S         
client=4 e2	client_mean=PT0.6S         
client=5 e1	client_mean=PT0.6S         
client=5 e2	client_mean=PT0.6S         
client=6 e1	client_mean=PT0.6S         
client=6 e2	client_mean=PT0.6S         
client=7 e1	client_mean=PT0.6S         
client=7 e2	client_mean=PT0.6S         
client=8 e1	client_mean=PT0.6S         
client=8 e2	client_mean=PT0.6S         
client=9 e1	client_mean=PT0.6S         
client=9 e2	client_mean=PT0.6S         

      server_side_rate_limits[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=100.0%	client_mean=PT52M50.064963128S	server_cpu=PT10145H30M    	client_received=150000/150000	server_resps=182619	codes={200=149974, 429=26}
client=0 endpoint	client_mean=PT51M0.50598548S
client=1 endpoint	client_mean=PT54M42.829428105S

          server_side_rate_limits[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=99.6%	client_mean=PT270H33M21.905926632S	server_cpu=PT12650H23M20S 	client_received=150000/150000	server_resps=227707	codes={200=149467, 429=533}
client=0 endpoint	client_mean=PT0S           
client=1 endpoint	client_mean=PT270H33M21.905926632S

                    server_side_rate_limits[UNLIMITED_ROUND_ROBIN].txt:	success=99.1%	client_mean=PT5M16.898919206S	server_cpu=PT13381H10M    	client_received=150000/150000	server_resps=240861	codes={200=148598, 429=1402}
client=0 endpoint	client_mean=PT5M17.863602531S
client=1 endpoint	client_mean=PT5M15.894516312S

server_side_rate_limits_with_sticky_clients_fairness_across_multiple_clients[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=78.4%	client_mean=PT33.794432133S	server_cpu=PT5M5.4S       	client_received=1411/1800	server_resps=2036	codes={200=1411}
client=0 endpoint	client_mean=PT32.903838017S
client=1 endpoint	client_mean=PT53.890040836S
client=2 endpoint	client_mean=PT12.424317266S
client=3 endpoint	client_mean=PT54.773662691S
client=4 endpoint	client_mean=PT42.907834985S
client=5 endpoint	client_mean=PT20.088368338S
client=6 endpoint	client_mean=PT43.8508015S  
client=7 endpoint	client_mean=PT37.341666905S
client=8 endpoint	client_mean=PT34.47941846S 
client=9 endpoint	client_mean=PT37.260095522S

server_side_rate_limits_with_sticky_clients_fairness_across_multiple_clients[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=78.4%	client_mean=PT33.794432133S	server_cpu=PT5M5.4S       	client_received=1411/1800	server_resps=2036	codes={200=1411}
client=0 endpoint	client_mean=PT32.903838017S
client=1 endpoint	client_mean=PT53.890040836S
client=2 endpoint	client_mean=PT12.424317266S
client=3 endpoint	client_mean=PT54.773662691S
client=4 endpoint	client_mean=PT42.907834985S
client=5 endpoint	client_mean=PT20.088368338S
client=6 endpoint	client_mean=PT43.8508015S  
client=7 endpoint	client_mean=PT37.341666905S
client=8 endpoint	client_mean=PT34.47941846S 
client=9 endpoint	client_mean=PT37.260095522S

server_side_rate_limits_with_sticky_clients_fairness_across_multiple_clients[UNLIMITED_ROUND_ROBIN].txt:	success=0.4%	client_mean=PT1.647136631S 	server_cpu=PT22M29.25S    	client_received=1800/1800	server_resps=8995	codes={200=7, 429=1793}
client=0 endpoint	client_mean=PT1.678304245S 
client=1 endpoint	client_mean=PT1.633060205S 
client=2 endpoint	client_mean=PT1.60214772S  
client=3 endpoint	client_mean=PT1.63370871S  
client=4 endpoint	client_mean=PT1.684219657S 
client=5 endpoint	client_mean=PT1.666007065S 
client=6 endpoint	client_mean=PT1.625101457S 
client=7 endpoint	client_mean=PT1.608858508S 
client=8 endpoint	client_mean=PT1.723365381S 
client=9 endpoint	client_mean=PT1.617480659S 

server_side_rate_limits_with_sticky_clients_steady_vs_bursty_client[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=96.9%	client_mean=PT25.042577608S	server_cpu=PT1M37.47S     	client_received=10060/10060	server_resps=19494	codes={200=9747, 429=313}
client=slowAndSteady endpoint	client_mean=PT0.514318735S 
client=oneShotBurst endpoint	client_mean=PT25.189747162S

server_side_rate_limits_with_sticky_clients_steady_vs_bursty_client[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=96.9%	client_mean=PT25.042577608S	server_cpu=PT1M37.47S     	client_received=10060/10060	server_resps=19494	codes={200=9747, 429=313}
client=slowAndSteady endpoint	client_mean=PT0.514318735S 
client=oneShotBurst endpoint	client_mean=PT25.189747162S

server_side_rate_limits_with_sticky_clients_steady_vs_bursty_client[UNLIMITED_ROUND_ROBIN].txt:	success=2.1%	client_mean=PT2.074470182S 	server_cpu=PT4M8.665S     	client_received=10060/10060	server_resps=49733	codes={200=210, 429=9850}
client=slowAndSteady endpoint	client_mean=PT0.035411019S 
client=oneShotBurst endpoint	client_mean=PT2.086704537S 

     short_outage_on_one_node[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=99.8%	client_mean=PT18.248406257S	server_cpu=PT53M14.00000003S	client_received=1600/1600	server_resps=1600	codes={200=1597, 500=3}
client=0 endpoint	client_mean=PT18.248406257S

         short_outage_on_one_node[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=99.4%	client_mean=PT4.35103125S  	server_cpu=PT53M0.0000001S	client_received=1600/1600	server_resps=1600	codes={200=1590, 500=10}
client=0 endpoint	client_mean=PT4.35103125S  

                   short_outage_on_one_node[UNLIMITED_ROUND_ROBIN].txt:	success=99.6%	client_mean=PT1.9925S      	server_cpu=PT53M8.00000006S	client_received=1600/1600	server_resps=1600	codes={200=1594, 500=6}
client=0 endpoint	client_mean=PT1.9925S      

       simplest_possible_case[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=100.0%	client_mean=PT0.834469696S 	server_cpu=PT3H3M35S      	client_received=13200/13200	server_resps=13200	codes={200=13200}
client=0 endpoint	client_mean=PT0.8S         
client=1 endpoint	client_mean=PT0.69484375S  
client=2 endpoint	client_mean=PT0.889355322S 
client=3 endpoint	client_mean=PT0.8S         
client=4 endpoint	client_mean=PT0.891629955S 
client=5 endpoint	client_mean=PT0.701850481S 
client=6 endpoint	client_mean=PT0.890408805S 
client=7 endpoint	client_mean=PT0.978419452S 
client=8 endpoint	client_mean=PT0.898151001S 
client=9 endpoint	client_mean=PT0.800738007S 

           simplest_possible_case[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=100.0%	client_mean=PT0.785757575S 	server_cpu=PT2H52M52S     	client_received=13200/13200	server_resps=13200	codes={200=13200}
client=0 endpoint	client_mean=PT0.788235294S 
client=1 endpoint	client_mean=PT0.78484375S  
client=2 endpoint	client_mean=PT0.78245877S  
client=3 endpoint	client_mean=PT0.789104477S 
client=4 endpoint	client_mean=PT0.783113069S 
client=5 endpoint	client_mean=PT0.781199111S 
client=6 endpoint	client_mean=PT0.794654088S 
client=7 endpoint	client_mean=PT0.788449848S 
client=8 endpoint	client_mean=PT0.78366718S  
client=9 endpoint	client_mean=PT0.782435424S 

                     simplest_possible_case[UNLIMITED_ROUND_ROBIN].txt:	success=100.0%	client_mean=PT0.785757575S 	server_cpu=PT2H52M52S     	client_received=13200/13200	server_resps=13200	codes={200=13200}
client=0 endpoint	client_mean=PT0.788235294S 
client=1 endpoint	client_mean=PT0.78484375S  
client=2 endpoint	client_mean=PT0.78245877S  
client=3 endpoint	client_mean=PT0.789104477S 
client=4 endpoint	client_mean=PT0.783113069S 
client=5 endpoint	client_mean=PT0.781199111S 
client=6 endpoint	client_mean=PT0.794654088S 
client=7 endpoint	client_mean=PT0.788449848S 
client=8 endpoint	client_mean=PT0.78366718S  
client=9 endpoint	client_mean=PT0.782435424S 

        slow_503s_then_revert[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=100.0%	client_mean=PT0.131980555S 	server_cpu=PT6M34.283333314S	client_received=3000/3000	server_resps=3076	codes={200=3000}
client=0 endpoint	client_mean=PT0.074665517S 
client=1 endpoint	client_mean=PT0.254970023S 
client=2 endpoint	client_mean=PT0.212862106S 
client=3 endpoint	client_mean=PT0.074389438S 
client=4 endpoint	client_mean=PT0.07434892S  
client=5 endpoint	client_mean=PT0.197480171S 
client=6 endpoint	client_mean=PT0.074873754S 
client=7 endpoint	client_mean=PT0.074198653S 
client=8 endpoint	client_mean=PT0.208951456S 
client=9 endpoint	client_mean=PT0.074518404S 

            slow_503s_then_revert[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=100.0%	client_mean=PT0.087459888S 	server_cpu=PT4M22.379666657S	client_received=3000/3000	server_resps=3031	codes={200=3000}
client=0 endpoint	client_mean=PT0.087563218S 
client=1 endpoint	client_mean=PT0.088243405S 
client=2 endpoint	client_mean=PT0.08698697S  
client=3 endpoint	client_mean=PT0.082067106S 
client=4 endpoint	client_mean=PT0.083556354S 
client=5 endpoint	client_mean=PT0.091266881S 
client=6 endpoint	client_mean=PT0.092342192S 
client=7 endpoint	client_mean=PT0.086086419S 
client=8 endpoint	client_mean=PT0.091847896S 
client=9 endpoint	client_mean=PT0.08443865S  

                      slow_503s_then_revert[UNLIMITED_ROUND_ROBIN].txt:	success=100.0%	client_mean=PT0.087459888S 	server_cpu=PT4M22.379666657S	client_received=3000/3000	server_resps=3031	codes={200=3000}
client=0 endpoint	client_mean=PT0.087563218S 
client=1 endpoint	client_mean=PT0.088243405S 
client=2 endpoint	client_mean=PT0.08698697S  
client=3 endpoint	client_mean=PT0.082067106S 
client=4 endpoint	client_mean=PT0.083556354S 
client=5 endpoint	client_mean=PT0.091266881S 
client=6 endpoint	client_mean=PT0.092342192S 
client=7 endpoint	client_mean=PT0.086086419S 
client=8 endpoint	client_mean=PT0.091847896S 
client=9 endpoint	client_mean=PT0.08443865S  

slowdown_and_error_thresholds[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=100.0%	client_mean=PT2M37.343650263S	server_cpu=PT9H13M35.659999222S	client_received=10000/10000	server_resps=11201	codes={200=10000}
client=0 endpoint	client_mean=PT2M5.737049292S
client=1 endpoint	client_mean=PT3M14.469768647S
client=2 endpoint	client_mean=PT2M24.56297476S
client=3 endpoint	client_mean=PT3M18.208862233S
client=4 endpoint	client_mean=PT3M19.683627089S
client=5 endpoint	client_mean=PT1M49.852285063S
client=6 endpoint	client_mean=PT1M53.716532194S
client=7 endpoint	client_mean=PT1M23.584824912S
client=8 endpoint	client_mean=PT3M43.234367602S
client=9 endpoint	client_mean=PT2M55.743423771S

    slowdown_and_error_thresholds[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=100.0%	client_mean=PT2M59.164157536S	server_cpu=PT12H27M9.866666568S	client_received=10000/10000	server_resps=11986	codes={200=9998, 500=2}
client=0 endpoint	client_mean=PT1M38.237753731S
client=1 endpoint	client_mean=PT3M41.728198493S
client=2 endpoint	client_mean=PT3M2.290618157S
client=3 endpoint	client_mean=PT3M9.718179589S
client=4 endpoint	client_mean=PT3M15.723782551S
client=5 endpoint	client_mean=PT2M32.513061148S
client=6 endpoint	client_mean=PT1M44.165083431S
client=7 endpoint	client_mean=PT3M49.225725925S
client=8 endpoint	client_mean=PT3M44.107628829S
client=9 endpoint	client_mean=PT3M7.968214842S

              slowdown_and_error_thresholds[UNLIMITED_ROUND_ROBIN].txt:	success=3.6%	client_mean=PT20.608783844S	server_cpu=PT54H52M58.19999995S	client_received=10000/10000	server_resps=49320	codes={200=357, 500=9643}
client=0 endpoint	client_mean=PT20.683115437S
client=1 endpoint	client_mean=PT20.935072092S
client=2 endpoint	client_mean=PT20.825737242S
client=3 endpoint	client_mean=PT20.773851863S
client=4 endpoint	client_mean=PT20.702693101S
client=5 endpoint	client_mean=PT20.790991491S
client=6 endpoint	client_mean=PT20.763874222S
client=7 endpoint	client_mean=PT19.956920066S
client=8 endpoint	client_mean=PT19.767051584S
client=9 endpoint	client_mean=PT20.872124059S

              uncommon_flakes[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=99.0%	client_mean=PT0.000001S    	server_cpu=PT0.01S        	client_received=10000/10000	server_resps=10000	codes={200=9900, 500=100}
client=0 endpoint	client_mean=PT0.000001S    
client=1 endpoint	client_mean=PT0.000001S    
client=2 endpoint	client_mean=PT0.000001S    
client=3 endpoint	client_mean=PT0.000001S    
client=4 endpoint	client_mean=PT0.000001S    
client=5 endpoint	client_mean=PT0.000001S    
client=6 endpoint	client_mean=PT0.000001S    
client=7 endpoint	client_mean=PT0.000001S    
client=8 endpoint	client_mean=PT0.000001S    
client=9 endpoint	client_mean=PT0.000001S    

                  uncommon_flakes[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=99.0%	client_mean=PT0.000001S    	server_cpu=PT0.01S        	client_received=10000/10000	server_resps=10000	codes={200=9900, 500=100}
client=0 endpoint	client_mean=PT0.000001S    
client=1 endpoint	client_mean=PT0.000001S    
client=2 endpoint	client_mean=PT0.000001S    
client=3 endpoint	client_mean=PT0.000001S    
client=4 endpoint	client_mean=PT0.000001S    
client=5 endpoint	client_mean=PT0.000001S    
client=6 endpoint	client_mean=PT0.000001S    
client=7 endpoint	client_mean=PT0.000001S    
client=8 endpoint	client_mean=PT0.000001S    
client=9 endpoint	client_mean=PT0.000001S    

                            uncommon_flakes[UNLIMITED_ROUND_ROBIN].txt:	success=99.0%	client_mean=PT0.000001S    	server_cpu=PT0.01S        	client_received=10000/10000	server_resps=10000	codes={200=9900, 500=100}
client=0 endpoint	client_mean=PT0.000001S    
client=1 endpoint	client_mean=PT0.000001S    
client=2 endpoint	client_mean=PT0.000001S    
client=3 endpoint	client_mean=PT0.000001S    
client=4 endpoint	client_mean=PT0.000001S    
client=5 endpoint	client_mean=PT0.000001S    
client=6 endpoint	client_mean=PT0.000001S    
client=7 endpoint	client_mean=PT0.000001S    
client=8 endpoint	client_mean=PT0.000001S    
client=9 endpoint	client_mean=PT0.000001S    

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


## `server_side_rate_limits_with_sticky_clients_fairness_across_multiple_clients[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/server_side_rate_limits_with_sticky_clients_fairness_across_multiple_clients[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td><td><image width=400 src="server_side_rate_limits_with_sticky_clients_fairness_across_multiple_clients[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td></tr></table>


## `server_side_rate_limits_with_sticky_clients_fairness_across_multiple_clients[CONCURRENCY_LIMITER_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/server_side_rate_limits_with_sticky_clients_fairness_across_multiple_clients[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td><td><image width=400 src="server_side_rate_limits_with_sticky_clients_fairness_across_multiple_clients[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td></tr></table>


## `server_side_rate_limits_with_sticky_clients_fairness_across_multiple_clients[UNLIMITED_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/server_side_rate_limits_with_sticky_clients_fairness_across_multiple_clients[UNLIMITED_ROUND_ROBIN].png" /></td><td><image width=400 src="server_side_rate_limits_with_sticky_clients_fairness_across_multiple_clients[UNLIMITED_ROUND_ROBIN].png" /></td></tr></table>


## `server_side_rate_limits_with_sticky_clients_steady_vs_bursty_client[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/server_side_rate_limits_with_sticky_clients_steady_vs_bursty_client[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td><td><image width=400 src="server_side_rate_limits_with_sticky_clients_steady_vs_bursty_client[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td></tr></table>


## `server_side_rate_limits_with_sticky_clients_steady_vs_bursty_client[CONCURRENCY_LIMITER_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/server_side_rate_limits_with_sticky_clients_steady_vs_bursty_client[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td><td><image width=400 src="server_side_rate_limits_with_sticky_clients_steady_vs_bursty_client[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td></tr></table>


## `server_side_rate_limits_with_sticky_clients_steady_vs_bursty_client[UNLIMITED_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/server_side_rate_limits_with_sticky_clients_steady_vs_bursty_client[UNLIMITED_ROUND_ROBIN].png" /></td><td><image width=400 src="server_side_rate_limits_with_sticky_clients_steady_vs_bursty_client[UNLIMITED_ROUND_ROBIN].png" /></td></tr></table>


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


