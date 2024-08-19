package org.ilt.fga;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

public class SpelParserTest {

  @Test
  void shouldReadObjectFieldAsExpression(){
    User user = new User("1", "test");
    Account account = new Account("ac1234");
    user.setAccount(account);
    StandardEvaluationContext context = new StandardEvaluationContext();
    context.setVariable("user",user);
    Expression expr = new SpelExpressionParser().parseExpression("#user.id");
    String name = expr.getValue(context, String.class);
    assertThat(name).isEqualTo("1");

    expr = new SpelExpressionParser().parseExpression("#user.account.accountId");
    String actId = expr.getValue(context, String.class);
    assertThat(actId).isEqualTo("ac1234");
  }

  public static class User {
    private final String id;
    private final String accountId;
    private Account account;

    public Account getAccount() {
      return account;
    }

    public void setAccount(Account account) {
      this.account = account;
    }

    public User(String id, String accountId) {
      this.id = id;
      this.accountId = accountId;
    }

    public String getId() {
      return id;
    }

    public String getAccountId() {
      return accountId;
    }
  }

  private static class Account {
    private final String accountId;

    private Account(String accountId) {
      this.accountId = accountId;
    }


    public String getAccountId() {
      return accountId;
    }
  }
}
