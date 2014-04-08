#!/bin/bash
#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
# 
#      http://www.apache.org/licenses/LICENSE-2.0
# 
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# Run all the queries and save a log. 
# First argument: Supply the folder which houses all the queries (recursive).
# Second argument: adds options to the VXQuery CLI.
#
# run_benchmark.sh ./noaa-ghcn-daily/benchmarks/local_speed_up/queries/
# run_benchmark.sh ./noaa-ghcn-daily/benchmarks/local_speed_up/queries/ "-client-net-ip-address 169.235.27.138"
# run_benchmark.sh ./noaa-ghcn-daily/benchmarks/local_speed_up/queries/ "" q03
#

CLUSTER_COUNT=5

if [ -z "${1}" ]
then
    echo "Please supply a directory for query files to be found."
    exit
fi

# Run queries for each number of nodes.
for (( i = 0; i < ${CLUSTER_COUNT}; i++ ))
do 
	echo "Starting ${i} cluster nodes"
	python vxquery-server/src/main/resources/scripts/cluster_cli.py -c vxquery-server/src/main/resources/conf/${i}nodes.xml -a start
	
	for j in $(find ${1} -name '*q??.xq')
	do
		# Only work with i nodes.
		if [[ "${j}" =~ "${i}nodes" ]] 
		then
			# Only run for specified queries.
			if [ -z "${3}" ] || [[ "${j}" =~ "${3}" ]] 
			then
				echo "Running query: ${j}"
				log_file="$(basename ${j}).$(date +%Y%m%d%H%M).log"
				log_base_path=$(dirname ${j/queries/query_logs})
				mkdir -p ${log_base_path}
				time sh ./vxquery-cli/target/appassembler/bin/vxq ${j} ${2} -timing -showquery -showoet -showrp -frame-size 10000 -repeatexec 10 > ${log_base_path}/${log_file} 2>&1
			fi;
		fi;
	done
	
	# Stop cluster.
	python vxquery-server/src/main/resources/scripts/cluster_cli.py -c vxquery-server/src/main/resources/conf/${i}nodes.xml -a stop
done

