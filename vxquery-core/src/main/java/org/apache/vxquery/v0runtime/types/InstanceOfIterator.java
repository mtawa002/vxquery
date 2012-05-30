/*
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements.  See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License.  You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package org.apache.vxquery.v0runtime.types;

import org.apache.vxquery.exceptions.SystemException;
import org.apache.vxquery.util.Filter;
import org.apache.vxquery.v0datamodel.XDMValue;
import org.apache.vxquery.v0datamodel.atomic.AtomicValueFactory;
import org.apache.vxquery.v0runtime.CallStackFrame;
import org.apache.vxquery.v0runtime.RegisterAllocator;
import org.apache.vxquery.v0runtime.base.AbstractEagerlyEvaluatedIterator;
import org.apache.vxquery.v0runtime.base.RuntimeIterator;

public class InstanceOfIterator extends AbstractEagerlyEvaluatedIterator {
    private RuntimeIterator input;
    private Filter<XDMValue> filter;

    public InstanceOfIterator(RegisterAllocator rAllocator, RuntimeIterator input, Filter<XDMValue> filter) {
        super(rAllocator);
        this.input = input;
        this.filter = filter;
    }

    @Override
    public Object evaluateEagerly(CallStackFrame frame) throws SystemException {
        final AtomicValueFactory atomicValueFactory = frame.getRuntimeControlBlock().getAtomicValueFactory();
        final boolean accept = filter.accept((XDMValue) input.evaluateEagerly(frame));
        return atomicValueFactory.createBoolean(accept);
    }
}