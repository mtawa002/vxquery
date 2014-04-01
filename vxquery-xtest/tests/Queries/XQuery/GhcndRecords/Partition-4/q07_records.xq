(: XQuery Self Join Query :)
(: Self join with all stations finding the difference in min and max       :)
(: temperature and get the average.                                        :)
    let $sensor_collection_min := "/Users/prestoncarman/Documents/smartsvn/vxquery_git_master/vxquery-xtest/tests/TestSources/ghcnd/half_1/quarter_1/|/Users/prestoncarman/Documents/smartsvn/vxquery_git_master/vxquery-xtest/tests/TestSources/ghcnd/half_1/quarter_2/|/Users/prestoncarman/Documents/smartsvn/vxquery_git_master/vxquery-xtest/tests/TestSources/ghcnd/half_2/quarter_3/|/Users/prestoncarman/Documents/smartsvn/vxquery_git_master/vxquery-xtest/tests/TestSources/ghcnd/half_2/quarter_4/"
    for $r_min in collection($sensor_collection_min)/dataCollection/data
    
    let $sensor_collection_max := "/Users/prestoncarman/Documents/smartsvn/vxquery_git_master/vxquery-xtest/tests/TestSources/ghcnd/half_1/quarter_1/|/Users/prestoncarman/Documents/smartsvn/vxquery_git_master/vxquery-xtest/tests/TestSources/ghcnd/half_1/quarter_2/|/Users/prestoncarman/Documents/smartsvn/vxquery_git_master/vxquery-xtest/tests/TestSources/ghcnd/half_2/quarter_3/|/Users/prestoncarman/Documents/smartsvn/vxquery_git_master/vxquery-xtest/tests/TestSources/ghcnd/half_2/quarter_4/"
    for $r_max in collection($sensor_collection_max)/dataCollection/data
    
    where $r_min/station eq $r_max/station
        and $r_min/date eq $r_max/date
        and $r_min/dataType eq "TMIN"
        and $r_max/dataType eq "TMAX"
    return $r_max/value - $r_min/value
