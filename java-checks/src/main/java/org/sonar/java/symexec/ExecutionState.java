/*
 * SonarQube Java
 * Copyright (C) 2012 SonarSource
 * dev@sonar.codehaus.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.java.symexec;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.Table;

import javax.annotation.Nullable;

import java.util.Map;
import java.util.Set;

import static org.sonar.java.symexec.SymbolicRelation.UNKNOWN;

public class ExecutionState {

  @Nullable
  @VisibleForTesting
  final ExecutionState parentState;
  @VisibleForTesting
  final Table<SymbolicValue, SymbolicValue, SymbolicRelation> relations;

  public ExecutionState() {
    this.parentState = null;
    this.relations = HashBasedTable.create();
  }

  ExecutionState(ExecutionState parentState) {
    this.parentState = parentState;
    this.relations = HashBasedTable.create();
  }

  @VisibleForTesting
  SymbolicRelation getRelation(SymbolicValue leftValue, SymbolicValue rightValue) {
    SymbolicRelation result = relations.get(leftValue, rightValue);
    return result != null ? result : parentState != null ? parentState.getRelation(leftValue, rightValue) : UNKNOWN;
  }

  SymbolicBooleanConstraint evaluateRelation(SymbolicValue leftValue, SymbolicRelation relation, SymbolicValue rightValue) {
    return getRelation(leftValue, rightValue).combine(relation);
  }

  ExecutionState setRelation(SymbolicValue leftValue, SymbolicRelation relation, SymbolicValue rightValue) {
    if (!leftValue.equals(rightValue)) {
      relations.put(leftValue, rightValue, relation);
      relations.put(rightValue, leftValue, relation.swap());
    }
    return this;
  }

  void mergeRelations(Iterable<ExecutionState> states) {
    for (Map.Entry<SymbolicValue, SymbolicValue> entry : findRelatedValues(states).entries()) {
      SymbolicRelation relation = null;
      for (ExecutionState state : states) {
        relation = state.getRelation(entry.getKey(), entry.getValue()).union(relation);
      }
      if (relation == null) {
        relation = SymbolicRelation.UNKNOWN;
      }
      if (getRelation(entry.getKey(), entry.getValue()) != relation) {
        relations.put(entry.getKey(), entry.getValue(), relation);
        relations.put(entry.getValue(), entry.getKey(), relation.swap());
      }
    }
  }

  private Multimap<SymbolicValue, SymbolicValue> findRelatedValues(Iterable<ExecutionState> states) {
    Multimap<SymbolicValue, SymbolicValue> result = HashMultimap.create();
    for (ExecutionState state : states) {
      for (ExecutionState current = state; !current.equals(this); current = current.parentState) {
        for (Map.Entry<SymbolicValue, Map<SymbolicValue, SymbolicRelation>> leftEntry : current.relations.rowMap().entrySet()) {
          result.putAll(leftEntry.getKey(), leftEntry.getValue().keySet());
        }
      }
    }
    return result;
  }

  SymbolicBooleanConstraint getBooleanConstraint(SymbolicValue.SymbolicVariableValue variable) {
    switch (getRelation(variable, SymbolicValue.BOOLEAN_TRUE)) {
      case EQUAL_TO:
        return SymbolicBooleanConstraint.TRUE;
      case NOT_EQUAL:
        return SymbolicBooleanConstraint.FALSE;
      default:
        return SymbolicBooleanConstraint.UNKNOWN;
    }
  }

  ExecutionState setBooleanConstraint(SymbolicValue.SymbolicVariableValue variable, SymbolicBooleanConstraint constraint) {
    switch (constraint) {
      case FALSE:
        setRelation(variable, SymbolicRelation.NOT_EQUAL, SymbolicValue.BOOLEAN_TRUE);
        break;
      case TRUE:
        setRelation(variable, SymbolicRelation.EQUAL_TO, SymbolicValue.BOOLEAN_TRUE);
        break;
      default:
        setRelation(variable, SymbolicRelation.UNKNOWN, SymbolicValue.BOOLEAN_TRUE);
        break;
    }
    return this;
  }

  void invalidateRelationsOnValue(SymbolicValue value) {
    Multimap<SymbolicValue, SymbolicValue> pairs = HashMultimap.create();
    for (ExecutionState current = this; current != null; current = current.parentState) {
      pairs.putAll(value, current.findRelatedValues(value));
    }
    for (Map.Entry<SymbolicValue, SymbolicValue> entry : pairs.entries()) {
      setRelation(entry.getKey(), SymbolicRelation.UNKNOWN, entry.getValue());
    }
  }

  private Set<SymbolicValue> findRelatedValues(SymbolicValue value) {
    Map<SymbolicValue, SymbolicRelation> map = relations.rowMap().get(value);
    return map != null ? map.keySet() : ImmutableSet.<SymbolicValue>of();
  }

  void invalidateFields() {
    for (ExecutionState state = this; state != null; state = state.parentState) {
      for (Map.Entry<SymbolicValue, Map<SymbolicValue, SymbolicRelation>> entry : state.relations.rowMap().entrySet()) {
        if (isField(entry.getKey())) {
          for (SymbolicValue other : entry.getValue().keySet()) {
            setRelation(entry.getKey(), SymbolicRelation.UNKNOWN, other);
          }
        }
      }
    }
  }

  private boolean isField(SymbolicValue value) {
    return value instanceof SymbolicValue.SymbolicVariableValue && ((SymbolicValue.SymbolicVariableValue) value).variable.owner().isTypeSymbol();
  }

}
