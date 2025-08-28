# Cursor IDE Setup Guide

This guide explains how to set up Cursor IDE to work with the WhatsApp Chat Viewer project and ensure all project rules are properly applied.

## 📋 Files Created for Cursor Integration

### 1. `.cursorrules` - AI Context File

- **Purpose**: Provides Cursor AI with project-specific rules and context
- **Location**: Project root directory
- **Content**: Security rules, coding standards, database patterns, etc.

### 2. `.editorconfig` - Editor Configuration

- **Purpose**: Ensures consistent code formatting across different editors
- **Location**: Project root directory
- **Features**:
  - 4-space indentation for Java files
  - 120 character line length
  - UTF-8 encoding
  - Unix line endings

### 3. `checkstyle.xml` - Code Quality Rules

- **Purpose**: Enforces Java coding standards and catches potential issues
- **Location**: Project root directory
- **Features**:
  - Naming conventions
  - Import organization
  - Code complexity checks
  - Security pattern validation

### 4. `.vscode/settings.json` - IDE Settings

- **Purpose**: Configures Cursor IDE for optimal Java development
- **Location**: `.vscode/` directory
- **Features**:
  - Java language support
  - Code formatting on save
  - Import organization
  - Linting configuration

### 5. `.git/hooks/pre-commit` - Pre-commit Hook

- **Purpose**: Runs code quality checks before commits
- **Location**: `.git/hooks/` directory
- **Features**:
  - Security pattern validation
  - Code quality checks
  - Commit message format validation

## 🚀 How to Apply These Rules in Cursor

### 1. Restart Cursor IDE

After adding these files, restart Cursor IDE to ensure all configurations are loaded:

```bash
# Close Cursor IDE completely and reopen the project
```

### 2. Verify AI Context is Loaded

Open any Java file and try asking Cursor AI to:

- Generate a new repository method
- Create a service method
- Add error handling to existing code

**Expected Behavior**: Cursor AI should:

- Always include `userId` parameter in database operations
- Use proper pagination for list operations
- Include appropriate logging
- Follow Java 21+ features
- Use Lombok annotations

### 3. Test the Rules

Try this prompt in Cursor AI:

```
"Create a new repository method to find chat entries by author"
```

**Expected Response**: Should include `userId` parameter and proper pagination:

```java
@Query("SELECT ce FROM ChatEntryEntity ce WHERE ce.userId = :userId AND ce.author = :author")
Page<ChatEntryEntity> findByUserIdAndAuthor(@Param("userId") Long userId,
                                           @Param("author") String author,
                                           Pageable pageable);
```

### 4. Verify Code Formatting

- Open any Java file
- Make some changes
- Save the file (Ctrl+S / Cmd+S)
- Code should be automatically formatted according to the rules

### 5. Check Pre-commit Hook

Try making a commit with a security violation:

```bash
# Add a file with a repository method missing userId
git add .
git commit -m "test: add repository method"
```

**Expected Result**: Pre-commit hook should warn about missing `userId` parameter.

## 🔧 Troubleshooting

### Cursor AI Not Following Rules

1. **Check `.cursorrules` file**: Ensure it's in the project root
2. **Restart Cursor**: Close and reopen the IDE
3. **Clear AI context**: Try asking Cursor to "forget previous context"
4. **Reference the rules**: Explicitly mention the rules in your prompt

### Code Formatting Not Working

1. **Check `.editorconfig`**: Ensure it's in the project root
2. **Verify VS Code settings**: Check `.vscode/settings.json`
3. **Install extensions**: Ensure Java extension pack is installed
4. **Check file associations**: Ensure `.java` files are recognized

### Pre-commit Hook Not Running

1. **Check permissions**: Ensure the hook is executable
   ```bash
   chmod +x .git/hooks/pre-commit
   ```
2. **Verify location**: Hook should be in `.git/hooks/pre-commit`
3. **Test manually**: Run the hook directly
   ```bash
   .git/hooks/pre-commit
   ```

## 📝 Best Practices for Using Cursor with This Project

### 1. Always Reference Security Rules

When asking Cursor to generate code, explicitly mention security:

```
"Create a service method to search chat entries. Remember to include userId for security."
```

### 2. Use Specific Prompts

Instead of: "Add a new endpoint"
Use: "Add a new REST endpoint for searching chat entries by keyword with userId authentication and pagination"

### 3. Verify Generated Code

Always review generated code for:

- ✅ `userId` parameter included
- ✅ Proper pagination
- ✅ Appropriate logging
- ✅ Error handling
- ✅ Security validation

### 4. Test Generated Code

Run the test suite to ensure generated code works correctly:

```bash
./mvnw test
```

## 🎯 Example Prompts That Work Well

### Repository Methods

```
"Create a repository method to find chat entries by date range for a specific user with pagination"
```

### Service Methods

```
"Create a service method to process file uploads with proper error handling and user validation"
```

### Controller Endpoints

```
"Create a REST endpoint to search chat entries with authentication, pagination, and proper error responses"
```

### Tests

```
"Create unit tests for the ChatEntryService with security validation and user isolation tests"
```

## 📚 Additional Resources

- [PROJECT_RULES.md](./PROJECT_RULES.md) - Complete project rules documentation
- [Cursor AI Documentation](https://cursor.sh/docs) - Official Cursor documentation
- [Java Coding Standards](https://google.github.io/styleguide/javaguide.html) - Google Java Style Guide

---

**Remember**: The `.cursorrules` file is the most important for AI assistance. Make sure it's always up to date with your project requirements!
