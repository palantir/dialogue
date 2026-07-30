# Report
<!-- Run SimulationTest and LoadSweepTest to regenerate this report. -->
```
                all_nodes_500[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=73.9%	client_mean=PT4.23716S     	server_cpu=PT20M          	client_received=2000/2000	server_resps=2000	codes={200=1477, 500=523}
client=0 endpoint	client_mean=PT3.548823529S 
client=1 endpoint	client_mean=PT3.491170212S 
client=2 endpoint	client_mean=PT3.681930693S 
client=3 endpoint	client_mean=PT4.455515463S 
client=4 endpoint	client_mean=PT3.855801104S 
client=5 endpoint	client_mean=PT4.737788018S 
client=6 endpoint	client_mean=PT3.922931937S 
client=7 endpoint	client_mean=PT4.824258373S 
client=8 endpoint	client_mean=PT4.446666666S 
client=9 endpoint	client_mean=PT5.126511627S 

                    all_nodes_500[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=74.0%	client_mean=PT3.33494S     	server_cpu=PT20M          	client_received=2000/2000	server_resps=2000	codes={200=1479, 500=521}
client=0 endpoint	client_mean=PT2.784010695S 
client=1 endpoint	client_mean=PT2.535053191S 
client=2 endpoint	client_mean=PT2.812227722S 
client=3 endpoint	client_mean=PT3.716391752S 
client=4 endpoint	client_mean=PT3.194640883S 
client=5 endpoint	client_mean=PT3.737050691S 
client=6 endpoint	client_mean=PT2.899109947S 
client=7 endpoint	client_mean=PT3.84507177S  
client=8 endpoint	client_mean=PT3.516388888S 
client=9 endpoint	client_mean=PT4.08172093S  

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

          burst_then_low_load[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=100.0%	client_mean=PT1.524824885S 	server_cpu=PT2M           	client_received=1200/1200	server_resps=1200	codes={200=1200}
client=burst endpoint	client_mean=PT1.788809696S 
client=lowload endpoint	client_mean=PT0.20490083S  

burst_then_low_load[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR]_slowstart.txt:	success=100.0%	client_mean=PT0.914754261S 	server_cpu=PT2M           	client_received=1200/1200	server_resps=1200	codes={200=1200}
client=burst endpoint	client_mean=PT1.076210029S 
client=lowload endpoint	client_mean=PT0.107475425S 

              burst_then_low_load[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=100.0%	client_mean=PT1.524824885S 	server_cpu=PT2M           	client_received=1200/1200	server_resps=1200	codes={200=1200}
client=burst endpoint	client_mean=PT1.788809696S 
client=lowload endpoint	client_mean=PT0.20490083S  

    burst_then_low_load[CONCURRENCY_LIMITER_ROUND_ROBIN]_slowstart.txt:	success=100.0%	client_mean=PT0.914754261S 	server_cpu=PT2M           	client_received=1200/1200	server_resps=1200	codes={200=1200}
client=burst endpoint	client_mean=PT1.076210029S 
client=lowload endpoint	client_mean=PT0.107475425S 

                        burst_then_low_load[UNLIMITED_ROUND_ROBIN].txt:	success=100.0%	client_mean=PT0.1S         	server_cpu=PT2M           	client_received=1200/1200	server_resps=1200	codes={200=1200}
client=burst endpoint	client_mean=PT0.1S         
client=lowload endpoint	client_mean=PT0.1S         

              burst_then_low_load[UNLIMITED_ROUND_ROBIN]_slowstart.txt:	success=100.0%	client_mean=PT0.1S         	server_cpu=PT2M           	client_received=1200/1200	server_resps=1200	codes={200=1200}
client=burst endpoint	client_mean=PT0.1S         
client=lowload endpoint	client_mean=PT0.1S         

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

                                                        load_sweep.txt:	strategy                                    rps     p99_ms    goodput/s  success%
CONCURRENCY_LIMITER_ROUND_ROBIN              25       66.0         25.0     100.0
CONCURRENCY_LIMITER_ROUND_ROBIN              50       72.0         49.9     100.0
CONCURRENCY_LIMITER_ROUND_ROBIN              75       78.0         74.8     100.0
CONCURRENCY_LIMITER_ROUND_ROBIN             100       90.0         99.6     100.0
CONCURRENCY_LIMITER_ROUND_ROBIN             125       96.0        124.5     100.0
CONCURRENCY_LIMITER_ROUND_ROBIN             150      114.0        149.2     100.0
CONCURRENCY_LIMITER_ROUND_ROBIN             175     6959.9        129.9     100.0
CONCURRENCY_LIMITER_ROUND_ROBIN             200    10883.9        129.3     100.0
CONCURRENCY_LIMITER_ROUND_ROBIN             250    19176.0        129.5     100.0
CONCURRENCY_LIMITER_ROUND_ROBIN             300    27706.7        130.2     100.0
CONCURRENCY_LIMITER_ROUND_ROBIN             400    44029.5        130.5     100.0
CONCURRENCY_LIMITER_ROUND_ROBIN             500    60004.6        130.6     100.0
CONCURRENCY_LIMITER_PIN_UNTIL_ERROR          25       72.0         25.0     100.0
CONCURRENCY_LIMITER_PIN_UNTIL_ERROR          50       90.0         49.8     100.0
CONCURRENCY_LIMITER_PIN_UNTIL_ERROR          75      114.0         74.6     100.0
CONCURRENCY_LIMITER_PIN_UNTIL_ERROR         100    10908.6         65.3     100.0
CONCURRENCY_LIMITER_PIN_UNTIL_ERROR         125    19414.0         65.2     100.0
CONCURRENCY_LIMITER_PIN_UNTIL_ERROR         150    27459.1         65.1     100.0
CONCURRENCY_LIMITER_PIN_UNTIL_ERROR         175    35790.9         65.3     100.0
CONCURRENCY_LIMITER_PIN_UNTIL_ERROR         200    43987.0         65.2     100.0
CONCURRENCY_LIMITER_PIN_UNTIL_ERROR         250    60025.6         65.3     100.0
CONCURRENCY_LIMITER_PIN_UNTIL_ERROR         300    75722.3         65.3     100.0
CONCURRENCY_LIMITER_PIN_UNTIL_ERROR         400   106888.5         65.5     100.0
CONCURRENCY_LIMITER_PIN_UNTIL_ERROR         500   137709.4         65.5     100.0
UNLIMITED_ROUND_ROBIN                        25       66.0         25.0     100.0
UNLIMITED_ROUND_ROBIN                        50       72.0         49.9     100.0
UNLIMITED_ROUND_ROBIN                        75       78.0         74.8     100.0
UNLIMITED_ROUND_ROBIN                       100       90.0         99.6     100.0
UNLIMITED_ROUND_ROBIN                       125       96.0        124.5     100.0
UNLIMITED_ROUND_ROBIN                       150      114.0        149.2     100.0
UNLIMITED_ROUND_ROBIN                       175     4738.8          8.3       5.7
UNLIMITED_ROUND_ROBIN                       200     4728.0          6.7       4.0
UNLIMITED_ROUND_ROBIN                       250     4740.0          5.5       2.7
UNLIMITED_ROUND_ROBIN                       300     4734.6          4.7       1.9
UNLIMITED_ROUND_ROBIN                       400     4738.8          3.8       1.2
UNLIMITED_ROUND_ROBIN                       500     4732.8          3.8       0.9

                                              load_sweep_slowstart.txt:	strategy                                    rps     p99_ms    goodput/s  success%
CONCURRENCY_LIMITER_ROUND_ROBIN              25       66.0         25.0     100.0
CONCURRENCY_LIMITER_ROUND_ROBIN              50       72.0         49.9     100.0
CONCURRENCY_LIMITER_ROUND_ROBIN              75       78.0         74.8     100.0
CONCURRENCY_LIMITER_ROUND_ROBIN             100       90.0         99.6     100.0
CONCURRENCY_LIMITER_ROUND_ROBIN             125       96.0        124.5     100.0
CONCURRENCY_LIMITER_ROUND_ROBIN             150      114.0        149.2     100.0
CONCURRENCY_LIMITER_ROUND_ROBIN             175     7051.4        129.8     100.0
CONCURRENCY_LIMITER_ROUND_ROBIN             200    11041.9        130.5     100.0
CONCURRENCY_LIMITER_ROUND_ROBIN             250    19312.6        130.4     100.0
CONCURRENCY_LIMITER_ROUND_ROBIN             300    27969.1        129.5     100.0
CONCURRENCY_LIMITER_ROUND_ROBIN             400    43781.4        130.2     100.0
CONCURRENCY_LIMITER_ROUND_ROBIN             500    59897.6        130.2     100.0
CONCURRENCY_LIMITER_PIN_UNTIL_ERROR          25       72.0         25.0     100.0
CONCURRENCY_LIMITER_PIN_UNTIL_ERROR          50       90.0         49.8     100.0
CONCURRENCY_LIMITER_PIN_UNTIL_ERROR          75      114.0         74.6     100.0
CONCURRENCY_LIMITER_PIN_UNTIL_ERROR         100    11105.9         65.3     100.0
CONCURRENCY_LIMITER_PIN_UNTIL_ERROR         125    19376.0         65.2     100.0
CONCURRENCY_LIMITER_PIN_UNTIL_ERROR         150    27961.6         65.0     100.0
CONCURRENCY_LIMITER_PIN_UNTIL_ERROR         175    35905.1         65.3     100.0
CONCURRENCY_LIMITER_PIN_UNTIL_ERROR         200    43792.0         65.0     100.0
CONCURRENCY_LIMITER_PIN_UNTIL_ERROR         250    59871.4         65.2     100.0
CONCURRENCY_LIMITER_PIN_UNTIL_ERROR         300    75789.0         65.3     100.0
CONCURRENCY_LIMITER_PIN_UNTIL_ERROR         400   106938.5         65.5     100.0
CONCURRENCY_LIMITER_PIN_UNTIL_ERROR         500   137691.4         65.5     100.0
UNLIMITED_ROUND_ROBIN                        25       66.0         25.0     100.0
UNLIMITED_ROUND_ROBIN                        50       72.0         49.9     100.0
UNLIMITED_ROUND_ROBIN                        75       78.0         74.8     100.0
UNLIMITED_ROUND_ROBIN                       100       90.0         99.6     100.0
UNLIMITED_ROUND_ROBIN                       125       96.0        124.5     100.0
UNLIMITED_ROUND_ROBIN                       150      114.0        149.2     100.0
UNLIMITED_ROUND_ROBIN                       175     4738.8          8.3       5.7
UNLIMITED_ROUND_ROBIN                       200     4728.0          6.7       4.0
UNLIMITED_ROUND_ROBIN                       250     4740.0          5.5       2.7
UNLIMITED_ROUND_ROBIN                       300     4734.6          4.7       1.9
UNLIMITED_ROUND_ROBIN                       400     4738.8          3.8       1.2
UNLIMITED_ROUND_ROBIN                       500     4732.8          3.8       0.9

                one_big_spike[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=100.0%	client_mean=PT2.667606696S 	server_cpu=PT2M30S        	client_received=1000/1000	server_resps=1000	codes={200=1000}
client=0 endpoint	client_mean=PT2.667606696S 

      one_big_spike[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR]_slowstart.txt:	success=100.0%	client_mean=PT1.639825596S 	server_cpu=PT2M30.45S     	client_received=1000/1000	server_resps=1003	codes={200=1000}
client=0 endpoint	client_mean=PT1.639825596S 

                    one_big_spike[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=100.0%	client_mean=PT1.579726259S 	server_cpu=PT2M30S        	client_received=1000/1000	server_resps=1000	codes={200=1000}
client=0 endpoint	client_mean=PT1.579726259S 

          one_big_spike[CONCURRENCY_LIMITER_ROUND_ROBIN]_slowstart.txt:	success=100.0%	client_mean=PT1.097365192S 	server_cpu=PT2M30S        	client_received=1000/1000	server_resps=1000	codes={200=1000}
client=0 endpoint	client_mean=PT1.097365192S 

                              one_big_spike[UNLIMITED_ROUND_ROBIN].txt:	success=99.9%	client_mean=PT1.220762244S 	server_cpu=PT8M28.2S      	client_received=1000/1000	server_resps=3388	codes={200=999, 429=1}
client=0 endpoint	client_mean=PT1.220762244S 

                    one_big_spike[UNLIMITED_ROUND_ROBIN]_slowstart.txt:	success=99.9%	client_mean=PT1.220762244S 	server_cpu=PT8M28.2S      	client_received=1000/1000	server_resps=3388	codes={200=999, 429=1}
client=0 endpoint	client_mean=PT1.220762244S 

one_endpoint_dies_on_each_server[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=66.0%	client_mean=PT2.0668656S   	server_cpu=PT25M          	client_received=2500/2500	server_resps=2500	codes={200=1651, 500=849}
client=0 e1	client_mean=PT1.837701492S 
client=0 e2	client_mean=PT1.603259259S 
client=1 e1	client_mean=PT2.485967213S 
client=1 e2	client_mean=PT2.081661538S 
client=2 e1	client_mean=PT1.640336134S 
client=2 e2	client_mean=PT1.626137404S 
client=3 e1	client_mean=PT1.995136S    
client=3 e2	client_mean=PT1.73S        
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

 one_endpoint_dies_on_each_server[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=66.4%	client_mean=PT1.9637952S   	server_cpu=PT25M          	client_received=2500/2500	server_resps=2500	codes={200=1661, 500=839}
client=0 e1	client_mean=PT1.801791044S 
client=0 e2	client_mean=PT1.526444444S 
client=1 e1	client_mean=PT2.556786885S 
client=1 e2	client_mean=PT2.191292307S 
client=2 e1	client_mean=PT2.310554621S 
client=2 e2	client_mean=PT1.809343511S 
client=3 e1	client_mean=PT1.883136S    
client=3 e2	client_mean=PT1.959230769S 
client=4 e1	client_mean=PT2.083134328S 
client=4 e2	client_mean=PT1.972608695S 
client=5 e1	client_mean=PT1.810123893S 
client=5 e2	client_mean=PT1.99008547S  
client=6 e1	client_mean=PT1.964716417S 
client=6 e2	client_mean=PT1.9467S      
client=7 e1	client_mean=PT1.374037735S 
client=7 e2	client_mean=PT0.937727272S 
client=8 e1	client_mean=PT2.685983193S 
client=8 e2	client_mean=PT2.616571428S 
client=9 e1	client_mean=PT1.950969696S 
client=9 e2	client_mean=PT1.864830769S 

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

      server_side_rate_limits[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=100.0%	client_mean=PT1H2M1.970134771S	server_cpu=PT10113H50M    	client_received=150000/150000	server_resps=182049	codes={200=149963, 429=37}
client=0 endpoint	client_mean=PT1H8M0.239577668S
client=1 endpoint	client_mean=PT55M50.537084583S

          server_side_rate_limits[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=99.8%	client_mean=PT86H6M10.565232062S	server_cpu=PT11623H10M    	client_received=150000/150000	server_resps=209217	codes={200=149737, 429=263}
client=0 endpoint	client_mean=PT46H54M51.72732875S
client=1 endpoint	client_mean=PT87H16M19.394154791S

                    server_side_rate_limits[UNLIMITED_ROUND_ROBIN].txt:	success=99.0%	client_mean=PT5M25.20993258S	server_cpu=PT13422H46M40S 	client_received=150000/150000	server_resps=241610	codes={200=148518, 429=1482}
client=0 endpoint	client_mean=PT5M22.124661S 
client=1 endpoint	client_mean=PT5M28.419374655S

server_side_rate_limits_with_sticky_clients_fairness_across_multiple_clients[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=88.3%	client_mean=PT32.03936578S 	server_cpu=PT5M58.95S     	client_received=1591/1800	server_resps=2393	codes={200=1590, 429=1}
client=0 endpoint	client_mean=PT34.856449434S
client=1 endpoint	client_mean=PT10.69250824S 
client=2 endpoint	client_mean=PT32.298337467S
client=3 endpoint	client_mean=PT28.660139918S
client=4 endpoint	client_mean=PT44.270166873S
client=5 endpoint	client_mean=PT37.949162014S
client=6 endpoint	client_mean=PT35.400377412S
client=7 endpoint	client_mean=PT31.165488981S
client=8 endpoint	client_mean=PT34.087246136S
client=9 endpoint	client_mean=PT32.237421318S

server_side_rate_limits_with_sticky_clients_fairness_across_multiple_clients[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=88.3%	client_mean=PT32.03936578S 	server_cpu=PT5M58.95S     	client_received=1591/1800	server_resps=2393	codes={200=1590, 429=1}
client=0 endpoint	client_mean=PT34.856449434S
client=1 endpoint	client_mean=PT10.69250824S 
client=2 endpoint	client_mean=PT32.298337467S
client=3 endpoint	client_mean=PT28.660139918S
client=4 endpoint	client_mean=PT44.270166873S
client=5 endpoint	client_mean=PT37.949162014S
client=6 endpoint	client_mean=PT35.400377412S
client=7 endpoint	client_mean=PT31.165488981S
client=8 endpoint	client_mean=PT34.087246136S
client=9 endpoint	client_mean=PT32.237421318S

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

server_side_rate_limits_with_sticky_clients_steady_vs_bursty_client[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=96.9%	client_mean=PT24.927819953S	server_cpu=PT1M37.475S    	client_received=10060/10060	server_resps=19495	codes={200=9749, 429=311}
client=slowAndSteady endpoint	client_mean=PT0.450916666S 
client=oneShotBurst endpoint	client_mean=PT25.074681373S

server_side_rate_limits_with_sticky_clients_steady_vs_bursty_client[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=96.9%	client_mean=PT24.927819953S	server_cpu=PT1M37.475S    	client_received=10060/10060	server_resps=19495	codes={200=9749, 429=311}
client=slowAndSteady endpoint	client_mean=PT0.450916666S 
client=oneShotBurst endpoint	client_mean=PT25.074681373S

server_side_rate_limits_with_sticky_clients_steady_vs_bursty_client[UNLIMITED_ROUND_ROBIN].txt:	success=6.4%	client_mean=PT3.873525187S 	server_cpu=PT4M1.57S      	client_received=10060/10060	server_resps=48314	codes={200=639, 429=9421}
client=slowAndSteady endpoint	client_mean=PT0.176960338S 
client=oneShotBurst endpoint	client_mean=PT3.895704576S 

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

slowdown_and_error_thresholds[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=100.0%	client_mean=PT2M35.964965526S	server_cpu=PT9H30M15.519999411S	client_received=10000/10000	server_resps=11194	codes={200=10000}
client=0 endpoint	client_mean=PT3M16.169803514S
client=1 endpoint	client_mean=PT3M4.524395617S
client=2 endpoint	client_mean=PT2M0.290283837S
client=3 endpoint	client_mean=PT2M45.263444325S
client=4 endpoint	client_mean=PT3M20.93566888S
client=5 endpoint	client_mean=PT3M0.806946548S
client=6 endpoint	client_mean=PT1M36.029018853S
client=7 endpoint	client_mean=PT1M17.895296292S
client=8 endpoint	client_mean=PT2M39.708601469S
client=9 endpoint	client_mean=PT2M52.459870124S

    slowdown_and_error_thresholds[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=100.0%	client_mean=PT2M59.457996717S	server_cpu=PT12H26M15.946666534S	client_received=10000/10000	server_resps=11961	codes={200=9999, 500=1}
client=0 endpoint	client_mean=PT3M36.070236426S
client=1 endpoint	client_mean=PT2M45.029604778S
client=2 endpoint	client_mean=PT2M55.059311259S
client=3 endpoint	client_mean=PT2M1.878558727S
client=4 endpoint	client_mean=PT3M28.496513414S
client=5 endpoint	client_mean=PT2M58.766239883S
client=6 endpoint	client_mean=PT2M55.930775235S
client=7 endpoint	client_mean=PT2M40.086622288S
client=8 endpoint	client_mean=PT4M22.970146505S
client=9 endpoint	client_mean=PT2M11.970795475S

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


## `burst_then_low_load[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/burst_then_low_load[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td><td><image width=400 src="burst_then_low_load[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td></tr></table>


## `burst_then_low_load[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR]_slowstart`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/burst_then_low_load[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR]_slowstart.png" /></td><td><image width=400 src="burst_then_low_load[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR]_slowstart.png" /></td></tr></table>


## `burst_then_low_load[CONCURRENCY_LIMITER_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/burst_then_low_load[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td><td><image width=400 src="burst_then_low_load[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td></tr></table>


## `burst_then_low_load[CONCURRENCY_LIMITER_ROUND_ROBIN]_slowstart`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/burst_then_low_load[CONCURRENCY_LIMITER_ROUND_ROBIN]_slowstart.png" /></td><td><image width=400 src="burst_then_low_load[CONCURRENCY_LIMITER_ROUND_ROBIN]_slowstart.png" /></td></tr></table>


## `burst_then_low_load[UNLIMITED_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/burst_then_low_load[UNLIMITED_ROUND_ROBIN].png" /></td><td><image width=400 src="burst_then_low_load[UNLIMITED_ROUND_ROBIN].png" /></td></tr></table>


## `burst_then_low_load[UNLIMITED_ROUND_ROBIN]_slowstart`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/burst_then_low_load[UNLIMITED_ROUND_ROBIN]_slowstart.png" /></td><td><image width=400 src="burst_then_low_load[UNLIMITED_ROUND_ROBIN]_slowstart.png" /></td></tr></table>


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


## `load_sweep`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/load_sweep.png" /></td><td><image width=400 src="load_sweep.png" /></td></tr></table>


## `load_sweep_slowstart`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/load_sweep_slowstart.png" /></td><td><image width=400 src="load_sweep_slowstart.png" /></td></tr></table>


## `one_big_spike[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/one_big_spike[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td><td><image width=400 src="one_big_spike[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td></tr></table>


## `one_big_spike[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR]_slowstart`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/one_big_spike[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR]_slowstart.png" /></td><td><image width=400 src="one_big_spike[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR]_slowstart.png" /></td></tr></table>


## `one_big_spike[CONCURRENCY_LIMITER_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/one_big_spike[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td><td><image width=400 src="one_big_spike[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td></tr></table>


## `one_big_spike[CONCURRENCY_LIMITER_ROUND_ROBIN]_slowstart`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/one_big_spike[CONCURRENCY_LIMITER_ROUND_ROBIN]_slowstart.png" /></td><td><image width=400 src="one_big_spike[CONCURRENCY_LIMITER_ROUND_ROBIN]_slowstart.png" /></td></tr></table>


## `one_big_spike[UNLIMITED_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/one_big_spike[UNLIMITED_ROUND_ROBIN].png" /></td><td><image width=400 src="one_big_spike[UNLIMITED_ROUND_ROBIN].png" /></td></tr></table>


## `one_big_spike[UNLIMITED_ROUND_ROBIN]_slowstart`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/one_big_spike[UNLIMITED_ROUND_ROBIN]_slowstart.png" /></td><td><image width=400 src="one_big_spike[UNLIMITED_ROUND_ROBIN]_slowstart.png" /></td></tr></table>


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


