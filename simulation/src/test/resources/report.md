# Report
<!-- Run SimulationTest to regenerate this report. -->
```
                all_nodes_500[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=73.9%	client_mean=PT3.09793S     	server_cpu=PT20M          	client_received=2000/2000	server_resps=2000	codes={200=1478, 500=522}
client=0 endpoint	client_mean=PT2.537967914S 
client=1 endpoint	client_mean=PT2.602446808S 
client=2 endpoint	client_mean=PT2.483712871S 
client=3 endpoint	client_mean=PT3.402010309S 
client=4 endpoint	client_mean=PT2.906298342S 
client=5 endpoint	client_mean=PT3.477695852S 
client=6 endpoint	client_mean=PT2.812931937S 
client=7 endpoint	client_mean=PT3.580956937S 
client=8 endpoint	client_mean=PT3.15699074S  
client=9 endpoint	client_mean=PT3.823255813S 

                    all_nodes_500[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=74.1%	client_mean=PT2.69369S     	server_cpu=PT20M          	client_received=2000/2000	server_resps=2000	codes={200=1481, 500=519}
client=0 endpoint	client_mean=PT2.346203208S 
client=1 endpoint	client_mean=PT2.189680851S 
client=2 endpoint	client_mean=PT2.130396039S 
client=3 endpoint	client_mean=PT3.072886597S 
client=4 endpoint	client_mean=PT2.631546961S 
client=5 endpoint	client_mean=PT2.981244239S 
client=6 endpoint	client_mean=PT2.362984293S 
client=7 endpoint	client_mean=PT3.079952153S 
client=8 endpoint	client_mean=PT2.75199074S  
client=9 endpoint	client_mean=PT3.245534883S 

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

                   black_hole[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=59.2%	client_mean=PT0.600617075S 	server_cpu=PT11M49.8S     	client_received=1183/2000	server_resps=1183	codes={200=1183}
client=0 endpoint	client_mean=PT0.6S         
client=1 endpoint	client_mean=PT0.600398936S 
client=2 endpoint	client_mean=PT0.6S         
client=3 endpoint	client_mean=PT0.6S         
client=4 endpoint	client_mean=PT0.6S         
client=5 endpoint	client_mean=PT0.601244239S 
client=6 endpoint	client_mean=PT0.6S         
client=7 endpoint	client_mean=PT0.6S         
client=8 endpoint	client_mean=PT0.600763888S 
client=9 endpoint	client_mean=PT0.603333333S 

                       black_hole[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=91.5%	client_mean=PT0.6S         	server_cpu=PT18M17.4S     	client_received=1829/2000	server_resps=1829	codes={200=1829}
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

             drastic_slowdown[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=100.0%	client_mean=PT2.947028083S 	server_cpu=PT41M8.862333314S	client_received=4000/4000	server_resps=4000	codes={200=4000}
client=0 endpoint	client_mean=PT0.068773437S 
client=1 endpoint	client_mean=PT7.331024888S 
client=2 endpoint	client_mean=PT7.636570261S 
client=3 endpoint	client_mean=PT0.068805486S 
client=4 endpoint	client_mean=PT0.06881491S  
client=5 endpoint	client_mean=PT7.149158998S 
client=6 endpoint	client_mean=PT0.068880407S 
client=7 endpoint	client_mean=PT0.068901265S 
client=8 endpoint	client_mean=PT6.876822712S 
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

               live_reloading[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=94.3%	client_mean=PT6.986924S    	server_cpu=PT1H56M8.59S   	client_received=2500/2500	server_resps=2500	codes={200=2357, 500=143}
client=0 endpoint	client_mean=PT7.895991902S 
client=1 endpoint	client_mean=PT6.775635555S 
client=2 endpoint	client_mean=PT6.516117187S 
client=3 endpoint	client_mean=PT6.636856S    
client=4 endpoint	client_mean=PT6.164213333S 
client=5 endpoint	client_mean=PT6.589072243S 
client=6 endpoint	client_mean=PT7.13207377S  
client=7 endpoint	client_mean=PT6.97628125S  
client=8 endpoint	client_mean=PT7.846233082S 
client=9 endpoint	client_mean=PT7.209014925S 

                   live_reloading[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=91.9%	client_mean=PT5.157092S    	server_cpu=PT1H54M27.31S  	client_received=2500/2500	server_resps=2500	codes={200=2298, 500=202}
client=0 endpoint	client_mean=PT4.579805668S 
client=1 endpoint	client_mean=PT4.84584S     
client=2 endpoint	client_mean=PT5.105140625S 
client=3 endpoint	client_mean=PT5.091392S    
client=4 endpoint	client_mean=PT5.016453333S 
client=5 endpoint	client_mean=PT5.071870722S 
client=6 endpoint	client_mean=PT5.043467213S 
client=7 endpoint	client_mean=PT5.7145625S   
client=8 endpoint	client_mean=PT5.103646616S 
client=9 endpoint	client_mean=PT5.887059701S 

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

                one_big_spike[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=100.0%	client_mean=PT2.569766928S 	server_cpu=PT2M30S        	client_received=1000/1000	server_resps=1000	codes={200=1000}
client=0 endpoint	client_mean=PT2.569766928S 

                    one_big_spike[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=100.0%	client_mean=PT1.521339525S 	server_cpu=PT2M30S        	client_received=1000/1000	server_resps=1000	codes={200=1000}
client=0 endpoint	client_mean=PT1.521339525S 

                              one_big_spike[UNLIMITED_ROUND_ROBIN].txt:	success=99.9%	client_mean=PT1.220762244S 	server_cpu=PT8M28.2S      	client_received=1000/1000	server_resps=3388	codes={200=999, 429=1}
client=0 endpoint	client_mean=PT1.220762244S 

one_endpoint_dies_on_each_server[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=66.5%	client_mean=PT2.0443184S   	server_cpu=PT25M          	client_received=2500/2500	server_resps=2500	codes={200=1663, 500=837}
client=0 e1	client_mean=PT1.598298507S 
client=0 e2	client_mean=PT1.348629629S 
client=1 e1	client_mean=PT2.879409836S 
client=1 e2	client_mean=PT2.442430769S 
client=2 e1	client_mean=PT1.735327731S 
client=2 e2	client_mean=PT1.627145038S 
client=3 e1	client_mean=PT2.40448S     
client=3 e2	client_mean=PT2.057076923S 
client=4 e1	client_mean=PT3.156268656S 
client=4 e2	client_mean=PT3.157536231S 
client=5 e1	client_mean=PT1.843079646S 
client=5 e2	client_mean=PT1.4048547S   
client=6 e1	client_mean=PT1.455850746S 
client=6 e2	client_mean=PT1.814833333S 
client=7 e1	client_mean=PT1.535622641S 
client=7 e2	client_mean=PT2.09460606S  
client=8 e1	client_mean=PT2.000941176S 
client=8 e2	client_mean=PT2.353365079S 
client=9 e1	client_mean=PT2.113787878S 
client=9 e2	client_mean=PT1.518676923S 

 one_endpoint_dies_on_each_server[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=65.7%	client_mean=PT2.0830368S   	server_cpu=PT25M          	client_received=2500/2500	server_resps=2500	codes={200=1643, 500=857}
client=0 e1	client_mean=PT1.79880597S  
client=0 e2	client_mean=PT1.347777777S 
client=1 e1	client_mean=PT2.810131147S 
client=1 e2	client_mean=PT2.425907692S 
client=2 e1	client_mean=PT2.264403361S 
client=2 e2	client_mean=PT1.790351145S 
client=3 e1	client_mean=PT1.744992S    
client=3 e2	client_mean=PT1.976646153S 
client=4 e1	client_mean=PT2.104179104S 
client=4 e2	client_mean=PT1.969449275S 
client=5 e1	client_mean=PT2.10619469S  
client=5 e2	client_mean=PT2.206290598S 
client=6 e1	client_mean=PT2.34922388S  
client=6 e2	client_mean=PT2.2214S      
client=7 e1	client_mean=PT1.74381132S  
client=7 e2	client_mean=PT1.158848484S 
client=8 e1	client_mean=PT2.436067226S 
client=8 e2	client_mean=PT2.418412698S 
client=9 e1	client_mean=PT2.300212121S 
client=9 e2	client_mean=PT2.433876923S 

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

      server_side_rate_limits[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=100.0%	client_mean=PT18M53.457866002S	server_cpu=PT9828H33M20S  	client_received=150000/150000	server_resps=176914	codes={200=149993, 429=7}
client=0 endpoint	client_mean=PT18M59.072457745S
client=1 endpoint	client_mean=PT18M47.669992494S

          server_side_rate_limits[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=99.9%	client_mean=PT8M32.186926191S	server_cpu=PT11146H50M    	client_received=150000/150000	server_resps=200643	codes={200=149814, 429=186}
client=0 endpoint	client_mean=PT9M9.814632081S
client=1 endpoint	client_mean=PT7M52.958231626S

                    server_side_rate_limits[UNLIMITED_ROUND_ROBIN].txt:	success=99.0%	client_mean=PT5M25.20993258S	server_cpu=PT13422H46M40S 	client_received=150000/150000	server_resps=241610	codes={200=148518, 429=1482}
client=0 endpoint	client_mean=PT5M22.124661S 
client=1 endpoint	client_mean=PT5M28.419374655S

server_side_rate_limits_with_sticky_clients_stready_vs_bursty_client[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=97.0%	client_mean=PT24.931845968S	server_cpu=PT1M37.525S    	client_received=10060/10060	server_resps=19505	codes={200=9755, 429=305}
client=slowAndSteady endpoint	client_mean=PT14.795458286S
client=oneShotBurst endpoint	client_mean=PT24.992664294S

server_side_rate_limits_with_sticky_clients_stready_vs_bursty_client[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=97.0%	client_mean=PT24.931845968S	server_cpu=PT1M37.525S    	client_received=10060/10060	server_resps=19505	codes={200=9755, 429=305}
client=slowAndSteady endpoint	client_mean=PT14.795458286S
client=oneShotBurst endpoint	client_mean=PT24.992664294S

server_side_rate_limits_with_sticky_clients_stready_vs_bursty_client[UNLIMITED_ROUND_ROBIN].txt:	success=0.8%	client_mean=PT1.88615825S  	server_cpu=PT4M10.37S     	client_received=10060/10060	server_resps=50074	codes={200=77, 429=9983}
client=slowAndSteady endpoint	client_mean=PT0.11156992S  
client=oneShotBurst endpoint	client_mean=PT1.89680578S  

     short_outage_on_one_node[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=99.9%	client_mean=PT16.216093757S	server_cpu=PT53M18.00000001S	client_received=1600/1600	server_resps=1600	codes={200=1599, 500=1}
client=0 endpoint	client_mean=PT16.216093757S

         short_outage_on_one_node[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=99.4%	client_mean=PT3.8120625S   	server_cpu=PT53M0.0000001S	client_received=1600/1600	server_resps=1600	codes={200=1590, 500=10}
client=0 endpoint	client_mean=PT3.8120625S   

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

slowdown_and_error_thresholds[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].txt:	success=100.0%	client_mean=PT2M55.45593999S	server_cpu=PT11H40M11.22666636S	client_received=10000/10000	server_resps=12265	codes={200=9996, 500=4}
client=0 endpoint	client_mean=PT2M49.809578174S
client=1 endpoint	client_mean=PT3M12.633718001S
client=2 endpoint	client_mean=PT2M20.202032837S
client=3 endpoint	client_mean=PT3M35.040669309S
client=4 endpoint	client_mean=PT3M46.496339978S
client=5 endpoint	client_mean=PT2M49.656256557S
client=6 endpoint	client_mean=PT3M27.880381357S
client=7 endpoint	client_mean=PT1M12.435635308S
client=8 endpoint	client_mean=PT2M25.016576076S
client=9 endpoint	client_mean=PT3M32.924631781S

    slowdown_and_error_thresholds[CONCURRENCY_LIMITER_ROUND_ROBIN].txt:	success=99.9%	client_mean=PT3M20.617177217S	server_cpu=PT13H53M18.233333278S	client_received=10000/10000	server_resps=13241	codes={200=9992, 500=8}
client=0 endpoint	client_mean=PT3M19.663087558S
client=1 endpoint	client_mean=PT4M16.386504463S
client=2 endpoint	client_mean=PT1M43.457418581S
client=3 endpoint	client_mean=PT3M3.197242721S
client=4 endpoint	client_mean=PT3M26.66196292S
client=5 endpoint	client_mean=PT3M54.466762552S
client=6 endpoint	client_mean=PT2M11.646668131S
client=7 endpoint	client_mean=PT4M19.546913131S
client=8 endpoint	client_mean=PT3M13.541825501S
client=9 endpoint	client_mean=PT3M54.627606589S

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


## `server_side_rate_limits_with_sticky_clients_stready_vs_bursty_client[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/server_side_rate_limits_with_sticky_clients_stready_vs_bursty_client[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td><td><image width=400 src="server_side_rate_limits_with_sticky_clients_stready_vs_bursty_client[CONCURRENCY_LIMITER_PIN_UNTIL_ERROR].png" /></td></tr></table>


## `server_side_rate_limits_with_sticky_clients_stready_vs_bursty_client[CONCURRENCY_LIMITER_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/server_side_rate_limits_with_sticky_clients_stready_vs_bursty_client[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td><td><image width=400 src="server_side_rate_limits_with_sticky_clients_stready_vs_bursty_client[CONCURRENCY_LIMITER_ROUND_ROBIN].png" /></td></tr></table>


## `server_side_rate_limits_with_sticky_clients_stready_vs_bursty_client[UNLIMITED_ROUND_ROBIN]`
<table><tr><th>develop</th><th>current</th></tr>
<tr><td><image width=400 src="https://media.githubusercontent.com/media/palantir/dialogue/develop/simulation/src/test/resources/server_side_rate_limits_with_sticky_clients_stready_vs_bursty_client[UNLIMITED_ROUND_ROBIN].png" /></td><td><image width=400 src="server_side_rate_limits_with_sticky_clients_stready_vs_bursty_client[UNLIMITED_ROUND_ROBIN].png" /></td></tr></table>


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


