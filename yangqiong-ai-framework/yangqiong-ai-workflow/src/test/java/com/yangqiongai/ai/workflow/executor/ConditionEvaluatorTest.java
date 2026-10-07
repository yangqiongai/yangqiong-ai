/*
 * Copyright (C) 2026 yangqiong
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, version 3 of the License
 * only ("AGPL-3.0-only") and not any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.yangqiongai.ai.workflow.executor;

import com.yangqiongai.ai.workflow.model.WorkflowState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ConditionEvaluatorTest {

    private ConditionEvaluator evaluator;
    private WorkflowState state;

    @BeforeEach
    void setUp() {
        evaluator = new ConditionEvaluator();
        state = new WorkflowState();
    }

    // ==================== evaluate 方法测试 ====================
    // 比较表达式返回布尔分支键：真返回"true"、假返回"false"，供分支映射true/false路由

    @Nested
    @DisplayName("evaluate - 相等判断 (==)")
    class EqualityTests {

        @Test
        @DisplayName("数值相等: ${score} == 100 返回true")
        void evaluate_numericEquality() {
            state.setVariable("score", 100);
            String result = evaluator.evaluate("${score} == 100", state);
            assertThat(result).isEqualTo("true");
        }

        @Test
        @DisplayName("字符串相等: ${status} == active 返回true")
        void evaluate_stringEquality() {
            state.setVariable("status", "active");
            String result = evaluator.evaluate("${status} == active", state);
            assertThat(result).isEqualTo("true");
        }

        @Test
        @DisplayName("相等判断不匹配时返回false")
        void evaluate_equalityNotMatched() {
            state.setVariable("score", 50);
            String result = evaluator.evaluate("${score} == 100", state);
            assertThat(result).isEqualTo("false");
        }
    }

    @Nested
    @DisplayName("evaluate - 不等判断 (!=)")
    class InequalityTests {

        @Test
        @DisplayName("${status} != error 当status为active时返回true")
        void evaluate_inequalityMatched() {
            state.setVariable("status", "active");
            String result = evaluator.evaluate("${status} != error", state);
            assertThat(result).isEqualTo("true");
        }

        @Test
        @DisplayName("${status} != error 当status为error时返回false")
        void evaluate_inequalityNotMatched() {
            state.setVariable("status", "error");
            String result = evaluator.evaluate("${status} != error", state);
            assertThat(result).isEqualTo("false");
        }
    }

    @Nested
    @DisplayName("evaluate - 数值比较 (>, <, >=, <=)")
    class NumericComparisonTests {

        @Test
        @DisplayName("${count} > 5 当count=10时返回true")
        void evaluate_greaterThan() {
            state.setVariable("count", 10);
            String result = evaluator.evaluate("${count} > 5", state);
            assertThat(result).isEqualTo("true");
        }

        @Test
        @DisplayName("${count} > 5 当count=3时返回false")
        void evaluate_greaterThanNotMatched() {
            state.setVariable("count", 3);
            String result = evaluator.evaluate("${count} > 5", state);
            assertThat(result).isEqualTo("false");
        }

        @Test
        @DisplayName("${count} < 10 当count=5时返回true")
        void evaluate_lessThan() {
            state.setVariable("count", 5);
            String result = evaluator.evaluate("${count} < 10", state);
            assertThat(result).isEqualTo("true");
        }

        @Test
        @DisplayName("${count} < 10 当count=15时返回false")
        void evaluate_lessThanNotMatched() {
            state.setVariable("count", 15);
            String result = evaluator.evaluate("${count} < 10", state);
            assertThat(result).isEqualTo("false");
        }

        @Test
        @DisplayName("${count} >= 5 当count=5时返回true（边界值）")
        void evaluate_greaterThanOrEqual_boundary() {
            state.setVariable("count", 5);
            String result = evaluator.evaluate("${count} >= 5", state);
            assertThat(result).isEqualTo("true");
        }

        @Test
        @DisplayName("${count} >= 5 当count=6时返回true")
        void evaluate_greaterThanOrEqual_greater() {
            state.setVariable("count", 6);
            String result = evaluator.evaluate("${count} >= 5", state);
            assertThat(result).isEqualTo("true");
        }

        @Test
        @DisplayName("${count} >= 5 当count=4时返回false")
        void evaluate_greaterThanOrEqual_notMatched() {
            state.setVariable("count", 4);
            String result = evaluator.evaluate("${count} >= 5", state);
            assertThat(result).isEqualTo("false");
        }

        @Test
        @DisplayName("${count} <= 10 当count=10时返回true（边界值）")
        void evaluate_lessThanOrEqual_boundary() {
            state.setVariable("count", 10);
            String result = evaluator.evaluate("${count} <= 10", state);
            assertThat(result).isEqualTo("true");
        }

        @Test
        @DisplayName("${count} <= 10 当count=9时返回true")
        void evaluate_lessThanOrEqual_less() {
            state.setVariable("count", 9);
            String result = evaluator.evaluate("${count} <= 10", state);
            assertThat(result).isEqualTo("true");
        }

        @Test
        @DisplayName("${count} <= 10 当count=11时返回false")
        void evaluate_lessThanOrEqual_notMatched() {
            state.setVariable("count", 11);
            String result = evaluator.evaluate("${count} <= 10", state);
            assertThat(result).isEqualTo("false");
        }

        @Test
        @DisplayName("支持浮点数比较")
        void evaluate_floatingPointComparison() {
            state.setVariable("rate", 3.14);
            String result = evaluator.evaluate("${rate} > 3.0", state);
            assertThat(result).isEqualTo("true");
        }
    }

    @Nested
    @DisplayName("evaluate - 字符串操作 (contains, startsWith, endsWith)")
    class StringOperationTests {

        @Test
        @DisplayName("${name} contains John")
        void evaluate_contains() {
            state.setVariable("name", "John Doe");
            String result = evaluator.evaluate("${name} contains John", state);
            assertThat(result).isEqualTo("true");
        }

        @Test
        @DisplayName("${name} contains John 不匹配时返回false")
        void evaluate_containsNotMatched() {
            state.setVariable("name", "Jane Doe");
            String result = evaluator.evaluate("${name} contains John", state);
            assertThat(result).isEqualTo("false");
        }

        @Test
        @DisplayName("${name} startsWith Hello")
        void evaluate_startsWith() {
            state.setVariable("name", "Hello World");
            String result = evaluator.evaluate("${name} startsWith Hello", state);
            assertThat(result).isEqualTo("true");
        }

        @Test
        @DisplayName("${name} startsWith Hello 不匹配时返回false")
        void evaluate_startsWithNotMatched() {
            state.setVariable("name", "Goodbye World");
            String result = evaluator.evaluate("${name} startsWith Hello", state);
            assertThat(result).isEqualTo("false");
        }

        @Test
        @DisplayName("${name} endsWith World")
        void evaluate_endsWith() {
            state.setVariable("name", "Hello World");
            String result = evaluator.evaluate("${name} endsWith World", state);
            assertThat(result).isEqualTo("true");
        }

        @Test
        @DisplayName("${name} endsWith World 不匹配时返回false")
        void evaluate_endsWithNotMatched() {
            state.setVariable("name", "Hello Earth");
            String result = evaluator.evaluate("${name} endsWith World", state);
            assertThat(result).isEqualTo("false");
        }
    }

    @Nested
    @DisplayName("evaluate - 简单变量引用")
    class SimpleVariableReferenceTests {

        @Test
        @DisplayName("${status} 返回变量值字符串")
        void evaluate_simpleVariable() {
            state.setVariable("status", "running");
            String result = evaluator.evaluate("${status}", state);
            assertThat(result).isEqualTo("running");
        }

        @Test
        @DisplayName("${count} 数值变量返回字符串形式")
        void evaluate_numericVariable() {
            state.setVariable("count", 42);
            String result = evaluator.evaluate("${count}", state);
            assertThat(result).isEqualTo("42");
        }

        @Test
        @DisplayName("${flag} 布尔变量返回字符串形式")
        void evaluate_booleanVariable() {
            state.setVariable("flag", true);
            String result = evaluator.evaluate("${flag}", state);
            assertThat(result).isEqualTo("true");
        }
    }

    @Nested
    @DisplayName("evaluate - 空值和缺失变量处理")
    class NullAndMissingVariableTests {

        @Test
        @DisplayName("null表达式返回null")
        void evaluate_nullExpression() {
            String result = evaluator.evaluate(null, state);
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("空字符串表达式返回null")
        void evaluate_emptyExpression() {
            String result = evaluator.evaluate("", state);
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("变量不存在时，相等判断返回false")
        void evaluate_equalityWithMissingVariable() {
            String result = evaluator.evaluate("${missing} == value", state);
            assertThat(result).isEqualTo("false");
        }

        @Test
        @DisplayName("变量不存在时，不等判断返回true（null != expected 为 true）")
        void evaluate_inequalityWithMissingVariable() {
            String result = evaluator.evaluate("${missing} != value", state);
            assertThat(result).isEqualTo("true");
        }

        @Test
        @DisplayName("变量不存在时，简单引用返回null")
        void evaluate_simpleReferenceWithMissingVariable() {
            String result = evaluator.evaluate("${missing}", state);
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("变量值为null时，contains操作返回false")
        void evaluate_containsWithNullVariable() {
            // ConcurrentHashMap不允许null值，通过不设置变量来模拟null
            String result = evaluator.evaluate("${name} contains test", state);
            assertThat(result).isEqualTo("false");
        }

        @Test
        @DisplayName("变量值为null时，startsWith操作返回false")
        void evaluate_startsWithWithNullVariable() {
            String result = evaluator.evaluate("${name} startsWith test", state);
            assertThat(result).isEqualTo("false");
        }

        @Test
        @DisplayName("变量值为null时，endsWith操作返回false")
        void evaluate_endsWithWithNullVariable() {
            String result = evaluator.evaluate("${name} endsWith test", state);
            assertThat(result).isEqualTo("false");
        }

        @Test
        @DisplayName("变量不存在时，> 比较返回false（null视为小于任何值，比较结果为false）")
        void evaluate_greaterThanWithMissingVariable() {
            // 变量不存在时actualStr为null，compareNumbers返回-1，>比较结果为false
            String result = evaluator.evaluate("${count} > 5", state);
            assertThat(result).isEqualTo("false");
        }
    }

    @Nested
    @DisplayName("evaluate - 非比较表达式原样返回")
    class PlainExpressionTests {

        @Test
        @DisplayName("不匹配任何模式的表达式原样返回")
        void evaluate_plainText() {
            String result = evaluator.evaluate("hello world", state);
            assertThat(result).isEqualTo("hello world");
        }

        @Test
        @DisplayName("带空格的表达式trim后原样返回")
        void evaluate_trimmedPlainText() {
            String result = evaluator.evaluate("  hello world  ", state);
            assertThat(result).isEqualTo("  hello world  ");
        }
    }

    // ==================== evaluateExitCondition 方法测试 ====================

    @Nested
    @DisplayName("evaluateExitCondition - 布尔变量")
    class ExitConditionBooleanTests {

        @Test
        @DisplayName("布尔变量为true时返回true")
        void exitCondition_booleanTrue() {
            state.setVariable("done", true);
            boolean result = evaluator.evaluateExitCondition("${done}", state);
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("布尔变量为false时返回false")
        void exitCondition_booleanFalse() {
            state.setVariable("done", false);
            boolean result = evaluator.evaluateExitCondition("${done}", state);
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("字符串'true'作为变量值时返回true")
        void exitCondition_stringTrue() {
            state.setVariable("done", "true");
            boolean result = evaluator.evaluateExitCondition("${done}", state);
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("字符串'false'作为变量值时返回false")
        void exitCondition_stringFalse() {
            state.setVariable("done", "false");
            boolean result = evaluator.evaluateExitCondition("${done}", state);
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("字符串'FALSE'（大写）作为变量值时返回false")
        void exitCondition_stringFalseUppercase() {
            state.setVariable("done", "FALSE");
            boolean result = evaluator.evaluateExitCondition("${done}", state);
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("非布尔非false字符串作为变量值时返回true")
        void exitCondition_nonBooleanString() {
            state.setVariable("done", "yes");
            boolean result = evaluator.evaluateExitCondition("${done}", state);
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("变量为null时返回false")
        void exitCondition_nullVariable() {
            // ConcurrentHashMap不允许null值，通过不设置变量来模拟null
            boolean result = evaluator.evaluateExitCondition("${done}", state);
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("变量不存在时返回false")
        void exitCondition_missingVariable() {
            boolean result = evaluator.evaluateExitCondition("${done}", state);
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("evaluateExitCondition - 比较表达式")
    class ExitConditionComparisonTests {

        @Test
        @DisplayName("${score} == 100 匹配时返回true")
        void exitCondition_equalityTrue() {
            state.setVariable("score", 100);
            boolean result = evaluator.evaluateExitCondition("${score} == 100", state);
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("${score} == 100 不匹配时返回false")
        void exitCondition_equalityFalse() {
            state.setVariable("score", 50);
            boolean result = evaluator.evaluateExitCondition("${score} == 100", state);
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("${count} > 5 匹配时返回true")
        void exitCondition_greaterThanTrue() {
            state.setVariable("count", 10);
            boolean result = evaluator.evaluateExitCondition("${count} > 5", state);
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("${status} != error 匹配时返回true")
        void exitCondition_inequalityTrue() {
            state.setVariable("status", "active");
            boolean result = evaluator.evaluateExitCondition("${status} != error", state);
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("${name} contains John 匹配时返回true")
        void exitCondition_containsTrue() {
            state.setVariable("name", "John Doe");
            boolean result = evaluator.evaluateExitCondition("${name} contains John", state);
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("${name} startsWith Hello 匹配时返回true")
        void exitCondition_startsWithTrue() {
            state.setVariable("name", "Hello World");
            boolean result = evaluator.evaluateExitCondition("${name} startsWith Hello", state);
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("${name} endsWith World 匹配时返回true")
        void exitCondition_endsWithTrue() {
            state.setVariable("name", "Hello World");
            boolean result = evaluator.evaluateExitCondition("${name} endsWith World", state);
            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("evaluateExitCondition - 空值和边界情况")
    class ExitConditionEdgeCaseTests {

        @Test
        @DisplayName("null退出条件返回false")
        void exitCondition_nullExpression() {
            boolean result = evaluator.evaluateExitCondition(null, state);
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("空字符串退出条件返回false")
        void exitCondition_emptyExpression() {
            boolean result = evaluator.evaluateExitCondition("", state);
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("不匹配任何模式的表达式返回false")
        void exitCondition_unmatchedExpression() {
            boolean result = evaluator.evaluateExitCondition("some random text", state);
            assertThat(result).isFalse();
        }
    }

    // ==================== 不支持的运算符和非数字回退 ====================

    @Nested
    @DisplayName("不支持的运算符和非数字回退")
    class UnsupportedOperatorAndFallbackTests {

        @Test
        @DisplayName("不支持的运算符（如===）被解析为==，比较不匹配返回false")
        void evaluate_unsupportedOperator() {
            state.setVariable("value", 10);
            // "===" 中 "==" 被COMPARISON_PATTERN匹配为运算符，剩余 "= 10" 作为期望值
            // 因此 "10".equals("= 10") 不匹配，返回false
            String result = evaluator.evaluate("${value} === 10", state);
            assertThat(result).isEqualTo("false");
        }

        @Test
        @DisplayName("完全不匹配任何模式时返回原表达式")
        void evaluate_noPatternMatch() {
            String result = evaluator.evaluate("some text without variables", state);
            assertThat(result).isEqualTo("some text without variables");
        }

        @Test
        @DisplayName("非数字值使用>比较时回退到字符串比较")
        void evaluate_nonNumericGreaterThan() {
            state.setVariable("name", "zebra");
            String result = evaluator.evaluate("${name} > apple", state);
            // "zebra".compareTo("apple") > 0
            assertThat(result).isEqualTo("true");
        }

        @Test
        @DisplayName("非数字值使用<比较时回退到字符串比较")
        void evaluate_nonNumericLessThan() {
            state.setVariable("name", "apple");
            String result = evaluator.evaluate("${name} < zebra", state);
            // "apple".compareTo("zebra") < 0
            assertThat(result).isEqualTo("true");
        }

        @Test
        @DisplayName("非数字值使用>=比较时回退到字符串比较")
        void evaluate_nonNumericGreaterThanOrEqual() {
            state.setVariable("name", "zebra");
            String result = evaluator.evaluate("${name} >= apple", state);
            assertThat(result).isEqualTo("true");
        }

        @Test
        @DisplayName("非数字值使用<=比较时回退到字符串比较")
        void evaluate_nonNumericLessThanOrEqual() {
            state.setVariable("name", "apple");
            String result = evaluator.evaluate("${name} <= zebra", state);
            assertThat(result).isEqualTo("true");
        }

        @Test
        @DisplayName("非数字值使用>=比较，相等时匹配")
        void evaluate_nonNumericGreaterThanOrEqual_equal() {
            state.setVariable("name", "apple");
            String result = evaluator.evaluate("${name} >= apple", state);
            assertThat(result).isEqualTo("true");
        }

        @Test
        @DisplayName("数值变量以字符串形式存储时仍可正确比较")
        void evaluate_stringStoredNumber() {
            state.setVariable("count", "10");
            String result = evaluator.evaluate("${count} > 5", state);
            assertThat(result).isEqualTo("true");
        }
    }

    @Nested
    @DisplayName("evaluate - 嵌套属性访问")
    class NestedPropertyTests {

        @Test
        @DisplayName("${input.length} > 5 字符串长度比较为真")
        void evaluate_stringLengthTrue() {
            state.setVariable("input", "rrrrrr");
            String result = evaluator.evaluate("${input.length} > 5", state);
            assertThat(result).isEqualTo("true");
        }

        @Test
        @DisplayName("${input.length} > 5 字符串长度比较为假（边界值）")
        void evaluate_stringLengthFalse() {
            state.setVariable("input", "rrrrr");
            String result = evaluator.evaluate("${input.length} > 5", state);
            assertThat(result).isEqualTo("false");
        }

        @Test
        @DisplayName("${input.size} 集合长度比较")
        void evaluate_collectionSize() {
            state.setVariable("items", java.util.List.of("a", "b", "c"));
            String result = evaluator.evaluate("${items.size} >= 3", state);
            assertThat(result).isEqualTo("true");
        }

        @Test
        @DisplayName("${map.name} == VIP Map按键取值")
        void evaluate_mapProperty() {
            state.setVariable("user", java.util.Map.of("name", "VIP"));
            String result = evaluator.evaluate("${user.name} == VIP", state);
            assertThat(result).isEqualTo("true");
        }

        @Test
        @DisplayName("嵌套属性不存在时比较返回false")
        void evaluate_missingNestedProperty() {
            state.setVariable("user", java.util.Map.of("name", "VIP"));
            String result = evaluator.evaluate("${user.age} > 18", state);
            assertThat(result).isEqualTo("false");
        }

        @Test
        @DisplayName("上游节点输出变量带.时优先按完整变量名匹配")
        void evaluate_flatVariablePriority() {
            state.setVariable("agent_1.output", "hello");
            String result = evaluator.evaluate("${agent_1.output} == hello", state);
            assertThat(result).isEqualTo("true");
        }
    }

    @Nested
    @DisplayName("evaluate - 期望值引号处理")
    class QuoteStrippingTests {

        @Test
        @DisplayName("期望值带双引号时去引号比较")
        void evaluate_doubleQuotedExpected() {
            state.setVariable("name", "John Doe");
            String result = evaluator.evaluate("${name} == \"John Doe\"", state);
            assertThat(result).isEqualTo("true");
        }

        @Test
        @DisplayName("期望值带单引号时去引号比较")
        void evaluate_singleQuotedExpected() {
            state.setVariable("status", "active");
            String result = evaluator.evaluate("${status} == 'active'", state);
            assertThat(result).isEqualTo("true");
        }
    }
}
