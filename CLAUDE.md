# Project Rules

## Java Coding Standards

### Optional handling
- **Never** use `Optional.get()` — always use `Optional.orElseThrow()` instead.
- Project uses `modernizer-maven-plugin` which enforces this at build time.
- Even when guarded by `isPresent()`, still use `orElseThrow()` to satisfy the linter.

```java
// BAD - will fail modernizer check
if (opt.isPresent()) {
    opt.get().doSomething();
}

// GOOD
if (opt.isPresent()) {
    opt.orElseThrow().doSomething();
}

// BETTER - use functional style when possible
opt.ifPresent(v -> v.doSomething());
opt.map(V::something).orElse(defaultValue);
```
