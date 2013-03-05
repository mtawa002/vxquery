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
package org.apache.vxquery.compiler.rewriter.rules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import org.apache.commons.lang3.mutable.Mutable;
import org.apache.vxquery.compiler.rewriter.VXQueryOptimizationContext;
import org.apache.vxquery.compiler.rewriter.rules.propagationpolicies.cardinality.Cardinality;
import org.apache.vxquery.compiler.rewriter.rules.propagationpolicies.documentorder.DocumentOrder;
import org.apache.vxquery.functions.BuiltinOperators;
import org.apache.vxquery.functions.Function;

import edu.uci.ics.hyracks.algebricks.common.exceptions.AlgebricksException;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.ILogicalExpression;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.ILogicalOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.IOptimizationContext;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.LogicalExpressionTag;
import edu.uci.ics.hyracks.algebricks.core.algebra.base.LogicalOperatorTag;
import edu.uci.ics.hyracks.algebricks.core.algebra.expressions.AbstractFunctionCallExpression;
import edu.uci.ics.hyracks.algebricks.core.algebra.expressions.VariableReferenceExpression;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.logical.AbstractLogicalOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.logical.AggregateOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.logical.AssignOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.logical.NestedTupleSourceOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.logical.SubplanOperator;
import edu.uci.ics.hyracks.algebricks.core.algebra.operators.logical.UnnestOperator;
import edu.uci.ics.hyracks.algebricks.core.rewriter.base.IAlgebraicRewriteRule;

public class RemoveUnusedSortDistinctNodesRule implements IAlgebraicRewriteRule {

    @Override
    public boolean rewritePre(Mutable<ILogicalOperator> opRef, IOptimizationContext context) throws AlgebricksException {
        return false;
    }

    /**
     * Find where a sort distinct nodes is being used and not required based on input parameters.
     * Search pattern: assign [function-call: sort-distinct-nodes-asc-or-atomics]
     */
    @Override
    public boolean rewritePost(Mutable<ILogicalOperator> opRef, IOptimizationContext context) {
        // Do not process empty or nested tuple source.
        AbstractLogicalOperator op = (AbstractLogicalOperator) opRef.getValue();
        if (op.getOperatorTag() == LogicalOperatorTag.EMPTYTUPLESOURCE
                || op.getOperatorTag() == LogicalOperatorTag.NESTEDTUPLESOURCE) {
            return false;
        }

        // Initialization.
        VXQueryOptimizationContext vxqueryContext = (VXQueryOptimizationContext) context;

        // Find the available variables.
        HashMap<Integer, DocumentOrder> documentOrderVariables = getProducerDocumentOrderVariableMap(opRef.getValue(),
                vxqueryContext);
        Cardinality cardinalityVariable = getProducerCardinality(opRef.getValue(), vxqueryContext);

        // Update sort operator if found.
        int variableId = getOperatorSortDistinctNodesAscOrAtomicsArgumentVariableId(opRef);
        if (variableId > 0) {
            if (documentOrderVariables.get(variableId) == DocumentOrder.YES) {
                // Do not need to sort the result.
                // All the checks for this operation were done in the getOperatorSortDistinctNodesAscOrAtomicsArgumentVariableId function.
                AssignOperator assign = (AssignOperator) op;
                ILogicalExpression logicalExpression = (ILogicalExpression) assign.getExpressions().get(0).getValue();
                AbstractFunctionCallExpression functionCall = (AbstractFunctionCallExpression) logicalExpression;
                functionCall.setFunctionInfo(BuiltinOperators.DISTINCT_NODES_OR_ATOMICS);
            } else {
                // No change.
            }

        }

        // Now with the new operator, update the variable mappings.
        cardinalityVariable = updateCardinalityVariable(op, cardinalityVariable, vxqueryContext);
        updateDocumentOrderVariableMap(op, documentOrderVariables, cardinalityVariable, vxqueryContext);

        // Save propagated value.
        vxqueryContext.putDocumentOrderOperatorVariableMap(opRef.getValue(), documentOrderVariables);
        vxqueryContext.putCardinalityOperatorMap(opRef.getValue(), cardinalityVariable);
        return false;
    }

    private Cardinality updateCardinalityVariable(AbstractLogicalOperator op, Cardinality cardinalityVariable,
            VXQueryOptimizationContext vxqueryContext) {
        switch (op.getOperatorTag()) {
            case AGGREGATE:
                cardinalityVariable = Cardinality.ONE;
                break;
            case SUBPLAN:
                // Find the last operator to set a variable and call this function again.
                SubplanOperator subplan = (SubplanOperator) op;
                AbstractLogicalOperator lastOperator = (AbstractLogicalOperator) subplan.getNestedPlans().get(0)
                        .getRoots().get(0).getValue();
                cardinalityVariable = updateCardinalityVariable(lastOperator, cardinalityVariable, vxqueryContext);
                break;
            case UNNEST:
                cardinalityVariable = Cardinality.MANY;
                break;

            // The following operators do not change the variable.
            case ASSIGN:
            case CLUSTER:
            case DATASOURCESCAN:
            case DIE:
            case DISTINCT:
            case EMPTYTUPLESOURCE:
            case EXCHANGE:
            case EXTENSION_OPERATOR:
            case GROUP:
            case INDEX_INSERT_DELETE:
            case INNERJOIN:
            case INSERT_DELETE:
            case LEFTOUTERJOIN:
            case LIMIT:
            case NESTEDTUPLESOURCE:
            case ORDER:
            case PARTITIONINGSPLIT:
            case PROJECT:
            case REPLICATE:
            case RUNNINGAGGREGATE:
            case SCRIPT:
            case SELECT:
            case SINK:
            case UNIONALL:
            case UNNEST_MAP:
            case UPDATE:
            case WRITE:
            case WRITE_RESULT:
            default:
                break;
        }
        return cardinalityVariable;
    }

    private void updateDocumentOrderVariableMap(AbstractLogicalOperator op,
            HashMap<Integer, DocumentOrder> documentOrderVariables, Cardinality cardinalityVariable,
            VXQueryOptimizationContext vxqueryContext) {
        int variableId;
        DocumentOrder documentOrder;
        HashMap<Integer, DocumentOrder> documentOrderVariablesForOperator = getProducerDocumentOrderVariableMap(op,
                vxqueryContext);

        // Get the DocumentOrder from propagation.
        switch (op.getOperatorTag()) {
            case AGGREGATE:
                AggregateOperator aggregate = (AggregateOperator) op;
                ILogicalExpression aggregateLogicalExpression = (ILogicalExpression) aggregate.getExpressions().get(0)
                        .getValue();
                variableId = aggregate.getVariables().get(0).getId();
                documentOrder = propagateDocumentOrder(aggregateLogicalExpression, documentOrderVariablesForOperator);
                documentOrderVariables.put(variableId, documentOrder);
                break;
            case ASSIGN:
                AssignOperator assign = (AssignOperator) op;
                ILogicalExpression assignLogicalExpression = (ILogicalExpression) assign.getExpressions().get(0)
                        .getValue();
                variableId = assign.getVariables().get(0).getId();
                documentOrder = propagateDocumentOrder(assignLogicalExpression, documentOrderVariablesForOperator);
                documentOrderVariables.put(variableId, documentOrder);
                break;
            case SUBPLAN:
                // Find the last operator to set a variable and call this function again.
                SubplanOperator subplan = (SubplanOperator) op;
                AbstractLogicalOperator lastOperator = (AbstractLogicalOperator) subplan.getNestedPlans().get(0)
                        .getRoots().get(0).getValue();
                updateDocumentOrderVariableMap(lastOperator, documentOrderVariables, cardinalityVariable,
                        vxqueryContext);
                break;
            case UNNEST:
                // Get unnest item property.
                UnnestOperator unnest = (UnnestOperator) op;
                ILogicalExpression logicalExpression = (ILogicalExpression) unnest.getExpressionRef().getValue();
                variableId = unnest.getVariables().get(0).getId();
                documentOrder = propagateDocumentOrder(logicalExpression, documentOrderVariablesForOperator);

                // Reset properties based on unnest duplication.
                updateDocumentOrderVariables(documentOrderVariables, DocumentOrder.NO);
                documentOrderVariables.put(variableId, documentOrder);

                // Add position variable property.
                if (unnest.getPositionalVariable() != null) {
                    variableId = unnest.getPositionalVariable().getId();
                    documentOrderVariables.put(variableId, DocumentOrder.YES);
                }
                break;

            // The following operators do not change or add to the variable map.
            case EMPTYTUPLESOURCE:
            case NESTEDTUPLESOURCE:
            case WRITE:
                break;

            // The following operators have not been implemented.
            case CLUSTER:
            case DATASOURCESCAN:
            case DIE:
            case DISTINCT:
            case EXCHANGE:
            case EXTENSION_OPERATOR:
            case GROUP:
            case INDEX_INSERT_DELETE:
            case INNERJOIN:
            case INSERT_DELETE:
            case LEFTOUTERJOIN:
            case LIMIT:
            case ORDER:
            case PARTITIONINGSPLIT:
            case PROJECT:
            case REPLICATE:
            case RUNNINGAGGREGATE:
            case SCRIPT:
            case SELECT:
            case SINK:
            case UNIONALL:
            case UNNEST_MAP:
            case UPDATE:
            case WRITE_RESULT:
            default:
                throw new RuntimeException("Operator has not been implemented in rewrite rule.");
        }

    }

    /**
     * Sets all the variables to DocumentOrder.
     * 
     * @param documentOrderVariables
     * @param documentOrder
     */
    private void updateDocumentOrderVariables(HashMap<Integer, DocumentOrder> documentOrderVariables,
            DocumentOrder documentOrder) {
        for (Entry<Integer, DocumentOrder> entry : documentOrderVariables.entrySet()) {
            documentOrderVariables.put(entry.getKey(), documentOrder);
        }
    }

    /**
     * Get the DocumentOrder variable map of the parent operator.
     * 
     * @param op
     * @param vxqueryContext
     * @return
     */
    private HashMap<Integer, DocumentOrder> getProducerDocumentOrderVariableMap(ILogicalOperator op,
            VXQueryOptimizationContext vxqueryContext) {
        AbstractLogicalOperator producerOp = (AbstractLogicalOperator) op.getInputs().get(0).getValue();
        switch (producerOp.getOperatorTag()) {
            case EMPTYTUPLESOURCE:
                return new HashMap<Integer, DocumentOrder>();
            case NESTEDTUPLESOURCE:
                NestedTupleSourceOperator nestedTuplesource = (NestedTupleSourceOperator) producerOp;
                return getProducerDocumentOrderVariableMap(nestedTuplesource.getDataSourceReference().getValue(),
                        vxqueryContext);
            default:
                return new HashMap<Integer, DocumentOrder>(
                        vxqueryContext.getDocumentOrderOperatorVariableMap(producerOp));
        }
    }

    /**
     * Get the DocumentOrder variable map of the parent operator.
     * 
     * @param op
     * @param vxqueryContext
     * @return
     */
    private Cardinality getProducerCardinality(ILogicalOperator op, VXQueryOptimizationContext vxqueryContext) {
        AbstractLogicalOperator producerOp = (AbstractLogicalOperator) op.getInputs().get(0).getValue();
        switch (producerOp.getOperatorTag()) {
            case EMPTYTUPLESOURCE:
                return Cardinality.ONE;
            case NESTEDTUPLESOURCE:
                NestedTupleSourceOperator nestedTuplesource = (NestedTupleSourceOperator) producerOp;
                return getProducerCardinality(nestedTuplesource.getDataSourceReference().getValue(), vxqueryContext);
            default:
                return vxqueryContext.getCardinalityOperatorMap(producerOp);
        }
    }

    private DocumentOrder propagateDocumentOrder(ILogicalExpression expr, HashMap<Integer, DocumentOrder> variableMap) {
        DocumentOrder documentOrder = null;
        switch (expr.getExpressionTag()) {
            case FUNCTION_CALL:
                AbstractFunctionCallExpression functionCall = (AbstractFunctionCallExpression) expr;

                // Look up all arguments.
                List<DocumentOrder> argProperties = new ArrayList<DocumentOrder>();
                for (Mutable<ILogicalExpression> argExpr : functionCall.getArguments()) {
                    argProperties.add(propagateDocumentOrder(argExpr.getValue(), variableMap));
                }

                // Propagate the property.
                Function func = (Function) functionCall.getFunctionInfo();
                documentOrder = func.getDocumentOrderPropagationPolicy().propagate(argProperties);
                break;
            case VARIABLE:
                VariableReferenceExpression variableReference = (VariableReferenceExpression) expr;
                int argVariableId = variableReference.getVariableReference().getId();
                documentOrder = variableMap.get(argVariableId);
                break;
            case CONSTANT:
            default:
                documentOrder = DocumentOrder.YES;
                break;
        }
        return documentOrder;
    }

    private int getOperatorSortDistinctNodesAscOrAtomicsArgumentVariableId(Mutable<ILogicalOperator> opRef) {
        // Check if assign is for sort-distinct-nodes-asc-or-atomics.
        AbstractLogicalOperator op = (AbstractLogicalOperator) opRef.getValue();
        if (op.getOperatorTag() != LogicalOperatorTag.ASSIGN) {
            return 0;
        }
        AssignOperator assign = (AssignOperator) op;

        // Check to see if the expression is a function and
        // sort-distinct-nodes-asc-or-atomics.
        ILogicalExpression logicalExpression = (ILogicalExpression) assign.getExpressions().get(0).getValue();
        if (logicalExpression.getExpressionTag() != LogicalExpressionTag.FUNCTION_CALL) {
            return 0;
        }
        AbstractFunctionCallExpression functionCall = (AbstractFunctionCallExpression) logicalExpression;
        if (!functionCall.getFunctionIdentifier().equals(
                BuiltinOperators.SORT_DISTINCT_NODES_ASC_OR_ATOMICS.getFunctionIdentifier())) {
            return 0;
        }

        // Find the variable id used as the parameter.
        ILogicalExpression logicalExpression2 = (ILogicalExpression) functionCall.getArguments().get(0).getValue();
        if (logicalExpression2.getExpressionTag() != LogicalExpressionTag.VARIABLE) {
            return 0;
        }
        VariableReferenceExpression variableExpression = (VariableReferenceExpression) logicalExpression2;
        return variableExpression.getVariableReference().getId();
    }
}