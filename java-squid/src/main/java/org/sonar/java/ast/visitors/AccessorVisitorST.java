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
package org.sonar.java.ast.visitors;

import org.apache.commons.lang.StringUtils;
import org.sonar.plugins.java.api.tree.AssignmentExpressionTree;
import org.sonar.plugins.java.api.tree.ClassTree;
import org.sonar.plugins.java.api.tree.ExpressionStatementTree;
import org.sonar.plugins.java.api.tree.ExpressionTree;
import org.sonar.plugins.java.api.tree.IdentifierTree;
import org.sonar.plugins.java.api.tree.MemberSelectExpressionTree;
import org.sonar.plugins.java.api.tree.MethodTree;
import org.sonar.plugins.java.api.tree.Modifier;
import org.sonar.plugins.java.api.tree.PrimitiveTypeTree;
import org.sonar.plugins.java.api.tree.ReturnStatementTree;
import org.sonar.plugins.java.api.tree.StatementTree;
import org.sonar.plugins.java.api.tree.Tree;
import org.sonar.plugins.java.api.tree.VariableTree;

import java.util.Collections;
import java.util.List;

public class AccessorVisitorST extends SubscriptionVisitor {

  @Override
  public List<Tree.Kind> nodesToVisit() {
    return Collections.emptyList();
  }

  public boolean isAccessor(ClassTree classTree, MethodTree methodTree) {
    return isPublicMethod(methodTree) && methodTree.block() != null && (isGetter(classTree, methodTree) || isSetter(classTree, methodTree));
  }

  private boolean isPublicMethod(MethodTree methodTree) {
    return methodTree.is(Tree.Kind.METHOD) && methodTree.modifiers().modifiers().contains(Modifier.PUBLIC);
  }

  private boolean isSetter(ClassTree classTree, MethodTree methodTree) {
    return methodTree.simpleName().name().startsWith("set") && methodTree.parameters().size() == 1
        && returnTypeIs(methodTree, "void") && hasOneAssignementStatement(methodTree, classTree);
  }

  private boolean hasOneAssignementStatement(MethodTree methodTree, ClassTree classTree) {
    List<StatementTree> body = methodTree.block().body();
    return body.size() == 1 && body.get(0).is(Tree.Kind.EXPRESSION_STATEMENT) && ((ExpressionStatementTree) body.get(0)).expression().is(Tree.Kind.ASSIGNMENT)
        && referencePrivateProperty((AssignmentExpressionTree) ((ExpressionStatementTree) body.get(0)).expression(), classTree);
  }

  private boolean referencePrivateProperty(AssignmentExpressionTree assignmentExpressionTree, ClassTree classTree) {
    return referencePrivateProperty(assignmentExpressionTree.variable(), classTree);
  }

  private boolean isGetter(ClassTree classTree, MethodTree methodTree) {
    return methodTree.parameters().isEmpty() && hasOneReturnStatement(methodTree, classTree) && (isValidGetter(methodTree) || isBooleanGetter(methodTree));
  }

  private boolean isBooleanGetter(MethodTree methodTree) {
    return methodTree.simpleName().name().startsWith("is")
        && returnTypeIs(methodTree, "boolean");
  }

  private boolean isValidGetter(MethodTree methodTree) {
    return methodTree.simpleName().name().startsWith("get");
  }

  private boolean hasOneReturnStatement(MethodTree methodTree, ClassTree classTree) {
    List<StatementTree> body = methodTree.block().body();
    return body.size() == 1 && body.get(0).is(Tree.Kind.RETURN_STATEMENT) && referencePrivateProperty((ReturnStatementTree) body.get(0), classTree);
  }

  private boolean referencePrivateProperty(ReturnStatementTree returnStatementTree, ClassTree classTree) {
    ExpressionTree expression = returnStatementTree.expression();
    String variableName = "";
    if (expression == null) {
      return false;
    } else if(expression.is(Tree.Kind.IDENTIFIER)) {
      variableName = ((IdentifierTree)expression).name();
    }
    return !StringUtils.isEmpty(variableName) && referencePrivateProperty(variableName, classTree);
  }

  private boolean referencePrivateProperty(ExpressionTree expression, ClassTree classTree) {
    String variableReturned = "";
    if (expression.is(Tree.Kind.IDENTIFIER)) {
      variableReturned = ((IdentifierTree) expression).name();
    } else if (expression.is(Tree.Kind.MEMBER_SELECT)) {
      variableReturned = ((MemberSelectExpressionTree) expression).identifier().name();
    }
    return !StringUtils.isEmpty(variableReturned) && referencePrivateProperty(variableReturned, classTree);
  }

  private boolean referencePrivateProperty(String variableName, ClassTree classTree) {
    for (Tree member : classTree.members()) {
      if (member.is(Tree.Kind.VARIABLE)) {
        VariableTree variableTree = (VariableTree) member;
        if (variableTree.modifiers().modifiers().contains(Modifier.PRIVATE) && variableTree.simpleName().name().equals(variableName)) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean returnTypeIs(MethodTree methodTree, String expectedReturnType) {
    Tree returnType = methodTree.returnType();
    return returnType != null && returnType.is(Tree.Kind.PRIMITIVE_TYPE) && expectedReturnType.equals(((PrimitiveTypeTree) returnType).keyword().text());
  }
}
