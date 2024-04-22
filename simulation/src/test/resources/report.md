# Report
<!-- Run SimulationTest to regenerate this report. -->
```
                all_nodes_500[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=69.3%	client_mean=PT3.46515S     	server_cpu=PT20M          	client_received=2000/2000	server_resps=2000	codes={200=1385, 500=615}
client=0 endpoint	client_mean=PT2.883582887S 
client=1 endpoint	client_mean=PT2.562287234S 
client=2 endpoint	client_mean=PT3.092079207S 
client=3 endpoint	client_mean=PT3.538144329S 
client=4 endpoint	client_mean=PT3.098011049S 
client=5 endpoint	client_mean=PT3.979907834S 
client=6 endpoint	client_mean=PT3.382984293S 
client=7 endpoint	client_mean=PT3.948325358S 
client=8 endpoint	client_mean=PT3.791712962S 
client=9 endpoint	client_mean=PT4.109860465S 

                    all_nodes_500[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=69.3%	client_mean=PT2.63052S     	server_cpu=PT20M          	client_received=2000/2000	server_resps=2000	codes={200=1385, 500=615}
client=0 endpoint	client_mean=PT2.185454545S 
client=1 endpoint	client_mean=PT1.927234042S 
client=2 endpoint	client_mean=PT2.110742574S 
client=3 endpoint	client_mean=PT2.821597938S 
client=4 endpoint	client_mean=PT2.512596685S 
client=5 endpoint	client_mean=PT3.035806451S 
client=6 endpoint	client_mean=PT2.577853403S 
client=7 endpoint	client_mean=PT3.150861244S 
client=8 endpoint	client_mean=PT2.674583333S 
client=9 endpoint	client_mean=PT3.13544186S  

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

        fast_503s_then_revert[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=100.0%	client_mean=PT0.120012009S 	server_cpu=PT1H30M0.00000004S	client_received=45000/45000	server_resps=45004	codes={200=45000}
client=0 endpoint	client_mean=PT0.12S        
client=1 endpoint	client_mean=PT0.120031175S 
client=2 endpoint	client_mean=PT0.120042066S 
client=3 endpoint	client_mean=PT0.12S        
client=4 endpoint	client_mean=PT0.12S        
client=5 endpoint	client_mean=PT0.120037747S 
client=6 endpoint	client_mean=PT0.12S        
client=7 endpoint	client_mean=PT0.12S        
client=8 endpoint	client_mean=PT0.120008602S 
client=9 endpoint	client_mean=PT0.12S        

            fast_503s_then_revert[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=100.0%	client_mean=PT0.120107163S 	server_cpu=PT1H30M0.0000004S	client_received=45000/45000	server_resps=45040	codes={200=45000}
client=0 endpoint	client_mean=PT0.120065474S 
client=1 endpoint	client_mean=PT0.120136715S 
client=2 endpoint	client_mean=PT0.120138677S 
client=3 endpoint	client_mean=PT0.120060747S 
client=4 endpoint	client_mean=PT0.120107187S 
client=5 endpoint	client_mean=PT0.120125546S 
client=6 endpoint	client_mean=PT0.120090738S 
client=7 endpoint	client_mean=PT0.120079801S 
client=8 endpoint	client_mean=PT0.120149317S 
client=9 endpoint	client_mean=PT0.120116849S 

                      fast_503s_then_revert[UNLIMITED_ROUND_ROBIN].txt:	success=100.0%	client_mean=PT0.120107163S 	server_cpu=PT1H30M0.0000004S	client_received=45000/45000	server_resps=45040	codes={200=45000}
client=0 endpoint	client_mean=PT0.120065474S 
client=1 endpoint	client_mean=PT0.120136715S 
client=2 endpoint	client_mean=PT0.120138677S 
client=3 endpoint	client_mean=PT0.120060747S 
client=4 endpoint	client_mean=PT0.120107187S 
client=5 endpoint	client_mean=PT0.120125546S 
client=6 endpoint	client_mean=PT0.120090738S 
client=7 endpoint	client_mean=PT0.120079801S 
client=8 endpoint	client_mean=PT0.120149317S 
client=9 endpoint	client_mean=PT0.120116849S 

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

                              one_big_spike[UNLIMITED_ROUND_ROBIN].txt:	success=99.9%	client_mean=PT1.220762244S 	server_cpu=PT8M28.2S      	client_received=1000/1000	server_resps=3388	codes={200=999, 429=1}
client=0 endpoint	client_mean=PT1.220762244S 

one_endpoint_dies_on_each_server[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=65.0%	client_mean=PT1.5396272S   	server_cpu=PT25M          	client_received=2500/2500	server_resps=2500	codes={200=1626, 500=874}
client=0 e1	client_mean=PT1.420537313S 
client=0 e2	client_mean=PT1.498888888S 
client=1 e1	client_mean=PT2.067016393S 
client=1 e2	client_mean=PT1.942523076S 
client=2 e1	client_mean=PT1.260235294S 
client=2 e2	client_mean=PT1.546442748S 
client=3 e1	client_mean=PT1.796352S    
client=3 e2	client_mean=PT1.719507692S 
client=4 e1	client_mean=PT1.741671641S 
client=4 e2	client_mean=PT1.632086956S 
client=5 e1	client_mean=PT0.994654867S 
client=5 e2	client_mean=PT1.352752136S 
client=6 e1	client_mean=PT1.995014925S 
client=6 e2	client_mean=PT1.784966666S 
client=7 e1	client_mean=PT1.489169811S 
client=7 e2	client_mean=PT0.892333333S 
client=8 e1	client_mean=PT1.312470588S 
client=8 e2	client_mean=PT1.624063492S 
client=9 e1	client_mean=PT1.670484848S 
client=9 e2	client_mean=PT0.951784615S 

 one_endpoint_dies_on_each_server[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=65.0%	client_mean=PT1.5548096S   	server_cpu=PT25M          	client_received=2500/2500	server_resps=2500	codes={200=1626, 500=874}
client=0 e1	client_mean=PT1.292985074S 
client=0 e2	client_mean=PT1.339592592S 
client=1 e1	client_mean=PT1.797278688S 
client=1 e2	client_mean=PT1.890953846S 
client=2 e1	client_mean=PT1.828571428S 
client=2 e2	client_mean=PT1.405160305S 
client=3 e1	client_mean=PT1.410176S    
client=3 e2	client_mean=PT1.159661538S 
client=4 e1	client_mean=PT2.036208955S 
client=4 e2	client_mean=PT2.311420289S 
client=5 e1	client_mean=PT1.584814159S 
client=5 e2	client_mean=PT1.242222222S 
client=6 e1	client_mean=PT1.509164179S 
client=6 e2	client_mean=PT1.5136S      
client=7 e1	client_mean=PT1.068943396S 
client=7 e2	client_mean=PT0.988151515S 
client=8 e1	client_mean=PT1.627563025S 
client=8 e2	client_mean=PT1.696507936S 
client=9 e1	client_mean=PT1.415333333S 
client=9 e2	client_mean=PT1.8244S      

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

      server_side_rate_limits[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=100.0%	client_mean=PT48M54.248589094S	server_cpu=PT10102H43M20S 	client_received=150000/150000	server_resps=181849	codes={200=149964, 429=36}
client=0 endpoint	client_mean=PT43M28.488085221S
client=1 endpoint	client_mean=PT54M25.971953827S

          server_side_rate_limits[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=99.7%	client_mean=PT129H48M33.775394562S	server_cpu=PT12203H13M20S 	client_received=150000/150000	server_resps=219658	codes={200=149527, 429=473}
client=0 endpoint	client_mean=PT129H48M33.775394562S
client=1 endpoint	client_mean=PT0S           

                    server_side_rate_limits[UNLIMITED_ROUND_ROBIN].txt:	success=99.0%	client_mean=PT5M25.20993258S	server_cpu=PT13422H46M40S 	client_received=150000/150000	server_resps=241610	codes={200=148518, 429=1482}
client=0 endpoint	client_mean=PT5M22.124661S 
client=1 endpoint	client_mean=PT5M28.419374655S

server_side_rate_limits_with_sticky_clients_fairness_across_multiple_clients[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=87.9%	client_mean=PT32.014805055S	server_cpu=PT5M57.9S      	client_received=1587/1800	server_resps=2386	codes={200=1582, 429=5}
client=0 endpoint	client_mean=PT40.522515129S
client=1 endpoint	client_mean=PT37.92709459S 
client=2 endpoint	client_mean=PT24.556243361S
client=3 endpoint	client_mean=PT26.069052756S
client=4 endpoint	client_mean=PT39.733344462S
client=5 endpoint	client_mean=PT43.091902261S
client=6 endpoint	client_mean=PT22.99246461S 
client=7 endpoint	client_mean=PT48.734093819S
client=8 endpoint	client_mean=PT14.870607116S
client=9 endpoint	client_mean=PT33.757115017S

server_side_rate_limits_with_sticky_clients_fairness_across_multiple_clients[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=87.9%	client_mean=PT32.014805055S	server_cpu=PT5M57.9S      	client_received=1587/1800	server_resps=2386	codes={200=1582, 429=5}
client=0 endpoint	client_mean=PT40.522515129S
client=1 endpoint	client_mean=PT37.92709459S 
client=2 endpoint	client_mean=PT24.556243361S
client=3 endpoint	client_mean=PT26.069052756S
client=4 endpoint	client_mean=PT39.733344462S
client=5 endpoint	client_mean=PT43.091902261S
client=6 endpoint	client_mean=PT22.99246461S 
client=7 endpoint	client_mean=PT48.734093819S
client=8 endpoint	client_mean=PT14.870607116S
client=9 endpoint	client_mean=PT33.757115017S

server_side_rate_limits_with_sticky_clients_fairness_across_multiple_clients[UNLIMITED_ROUND_ROBIN].txt:	success=0.7%	client_mean=PT2.693485967S 	server_cpu=PT22M28.8S     	client_received=1800/1800	server_resps=8992	codes={200=12, 429=1788}
client=0 endpoint	client_mean=PT2.677130415S 
client=1 endpoint	client_mean=PT2.58706365S  
client=2 endpoint	client_mean=PT2.691622734S 
client=3 endpoint	client_mean=PT2.747010968S 
client=4 endpoint	client_mean=PT2.651957886S 
client=5 endpoint	client_mean=PT2.760794894S 
client=6 endpoint	client_mean=PT2.639373584S 
client=7 endpoint	client_mean=PT2.70673787S  
client=8 endpoint	client_mean=PT2.785595361S 
client=9 endpoint	client_mean=PT2.659295134S 

server_side_rate_limits_with_sticky_clients_steady_vs_bursty_client[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=96.9%	client_mean=PT24.926354146S	server_cpu=PT1M37.505S    	client_received=10060/10060	server_resps=19501	codes={200=9749, 429=311}
client=slowAndSteady endpoint	client_mean=PT0.609583333S 
client=oneShotBurst endpoint	client_mean=PT25.072254771S

server_side_rate_limits_with_sticky_clients_steady_vs_bursty_client[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=96.9%	client_mean=PT24.926354146S	server_cpu=PT1M37.505S    	client_received=10060/10060	server_resps=19501	codes={200=9749, 429=311}
client=slowAndSteady endpoint	client_mean=PT0.609583333S 
client=oneShotBurst endpoint	client_mean=PT25.072254771S

server_side_rate_limits_with_sticky_clients_steady_vs_bursty_client[UNLIMITED_ROUND_ROBIN].txt:	success=6.4%	client_mean=PT3.873525187S 	server_cpu=PT4M1.57S      	client_received=10060/10060	server_resps=48314	codes={200=639, 429=9421}
client=slowAndSteady endpoint	client_mean=PT0.176960338S 
client=oneShotBurst endpoint	client_mean=PT3.895704576S 

     short_outage_on_one_node[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=99.8%	client_mean=PT18.248406257S	server_cpu=PT53M14.00000003S	client_received=1600/1600	server_resps=1600	codes={200=1597, 500=3}
client=0 endpoint	client_mean=PT18.248406257S

         short_outage_on_one_node[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=99.4%	client_mean=PT3.973875S    	server_cpu=PT53M0.0000001S	client_received=1600/1600	server_resps=1600	codes={200=1590, 500=10}
client=0 endpoint	client_mean=PT3.973875S    

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

        slow_503s_then_revert[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=100.0%	client_mean=PT0.135264504S 	server_cpu=PT6M34.279333314S	client_received=3000/3000	server_resps=3076	codes={200=3000}
client=0 endpoint	client_mean=PT0.074582758S 
client=1 endpoint	client_mean=PT0.264328196S 
client=2 endpoint	client_mean=PT0.220026111S 
client=3 endpoint	client_mean=PT0.074349834S 
client=4 endpoint	client_mean=PT0.074356115S 
client=5 endpoint	client_mean=PT0.205764688S 
client=6 endpoint	client_mean=PT0.074817275S 
client=7 endpoint	client_mean=PT0.074296296S 
client=8 endpoint	client_mean=PT0.217040263S 
client=9 endpoint	client_mean=PT0.074509202S 

            slow_503s_then_revert[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=100.0%	client_mean=PT0.088828705S 	server_cpu=PT4M22.364666657S	client_received=3000/3000	server_resps=3031	codes={200=3000}
client=0 endpoint	client_mean=PT0.088755941S 
client=1 endpoint	client_mean=PT0.089185144S 
client=2 endpoint	client_mean=PT0.087887115S 
client=3 endpoint	client_mean=PT0.083460466S 
client=4 endpoint	client_mean=PT0.084995623S 
client=5 endpoint	client_mean=PT0.093498045S 
client=6 endpoint	client_mean=PT0.094040639S 
client=7 endpoint	client_mean=PT0.087503877S 
client=8 endpoint	client_mean=PT0.093271539S 
client=9 endpoint	client_mean=PT0.085463477S 

                      slow_503s_then_revert[UNLIMITED_ROUND_ROBIN].txt:	success=100.0%	client_mean=PT0.088828705S 	server_cpu=PT4M22.364666657S	client_received=3000/3000	server_resps=3031	codes={200=3000}
client=0 endpoint	client_mean=PT0.088755941S 
client=1 endpoint	client_mean=PT0.089185144S 
client=2 endpoint	client_mean=PT0.087887115S 
client=3 endpoint	client_mean=PT0.083460466S 
client=4 endpoint	client_mean=PT0.084995623S 
client=5 endpoint	client_mean=PT0.093498045S 
client=6 endpoint	client_mean=PT0.094040639S 
client=7 endpoint	client_mean=PT0.087503877S 
client=8 endpoint	client_mean=PT0.093271539S 
client=9 endpoint	client_mean=PT0.085463477S 

slowdown_and_error_thresholds[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=100.0%	client_mean=PT2M36.501145596S	server_cpu=PT9H17M30.066665962S	client_received=10000/10000	server_resps=11344	codes={200=10000}
client=0 endpoint	client_mean=PT2M55.312312992S
client=1 endpoint	client_mean=PT2M3.905223817S
client=2 endpoint	client_mean=PT2M32.641998031S
client=3 endpoint	client_mean=PT3M27.665446311S
client=4 endpoint	client_mean=PT3M26.495251024S
client=5 endpoint	client_mean=PT2M2.155634594S
client=6 endpoint	client_mean=PT1M27.725689076S
client=7 endpoint	client_mean=PT1M36.498117843S
client=8 endpoint	client_mean=PT3M5.299952281S
client=9 endpoint	client_mean=PT3M18.974909556S

    slowdown_and_error_thresholds[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=100.0%	client_mean=PT2M58.84480347S	server_cpu=PT12H27M36.573333219S	client_received=10000/10000	server_resps=12060	codes={200=9999, 500=1}
client=0 endpoint	client_mean=PT3M24.589807652S
client=1 endpoint	client_mean=PT3M17.539711156S
client=2 endpoint	client_mean=PT2M20.34871381S
client=3 endpoint	client_mean=PT2M38.457006718S
client=4 endpoint	client_mean=PT3M21.581879708S
client=5 endpoint	client_mean=PT3M9.603043627S
client=6 endpoint	client_mean=PT2M10.101157025S
client=7 endpoint	client_mean=PT2M49.912344107S
client=8 endpoint	client_mean=PT3M43.066995967S
client=9 endpoint	client_mean=PT2M51.337727021S

              slowdown_and_error_thresholds[UNLIMITED_ROUND_ROBIN].txt:	success=3.6%	client_mean=PT21.551691121S	server_cpu=PT54H45M57.899999949S	client_received=10000/10000	server_resps=49335	codes={200=360, 500=9640}
client=0 endpoint	client_mean=PT21.677123308S
client=1 endpoint	client_mean=PT21.812386309S
client=2 endpoint	client_mean=PT21.737091429S
client=3 endpoint	client_mean=PT21.844572743S
client=4 endpoint	client_mean=PT21.588830472S
client=5 endpoint	client_mean=PT21.772786346S
client=6 endpoint	client_mean=PT21.737197865S
client=7 endpoint	client_mean=PT20.383746516S
client=8 endpoint	client_mean=PT21.132447937S
client=9 endpoint	client_mean=PT21.815683512S

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


