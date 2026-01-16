# Contributing to Launch

Thank you for your interest in contributing to Launch! This document provides guidelines and instructions for contributing to the project.

## 📋 Table of Contents

- [Code of Conduct](#code-of-conduct)
- [How Can I Contribute?](#how-can-i-contribute)
- [Development Setup](#development-setup)
- [Development Workflow](#development-workflow)
- [Coding Standards](#coding-standards)
- [Commit Guidelines](#commit-guidelines)
- [Pull Request Process](#pull-request-process)
- [Reporting Bugs](#reporting-bugs)
- [Suggesting Features](#suggesting-features)

## Code of Conduct

By participating in this project, you agree to maintain a respectful and inclusive environment for all contributors.

## How Can I Contribute?

### Reporting Bugs

Before creating a bug report, please:
1. Check if the issue has already been reported
2. Test with the latest version
3. Gather relevant information (Android version, device model, steps to reproduce)

Use the [Bug Report template](.github/ISSUE_TEMPLATE/bug_report.md) when creating an issue.

### Suggesting Features

Feature suggestions are welcome! Please:
1. Check if the feature has already been suggested
2. Consider if it aligns with the project's goals (minimalist, efficient launcher)
3. Provide a clear description and use case

Use the [Feature Request template](.github/ISSUE_TEMPLATE/feature_request.md) when creating an issue.

### Pull Requests

Pull requests are the best way to contribute code. Please follow the [Pull Request Process](#pull-request-process) below.

## Development Setup

### Prerequisites

- **Android Studio** (Hedgehog or later)
- **JDK 11** or higher
- **Android SDK** (API 24+)
- **Git**

### Getting Started

1. **Fork the repository** on GitHub

2. **Clone your fork**:
   ```bash
   git clone https://github.com/YOUR_USERNAME/launch.git
   cd launch
   ```

3. **Add upstream remote**:
   ```bash
   git remote add upstream https://github.com/guruswarupa/launch.git
   ```

4. **Open in Android Studio**:
   - File → Open → Select the `launch` directory
   - Wait for Gradle sync to complete

5. **Build the project**:
   ```bash
   ./gradlew build
   ```

6. **Run on device/emulator**:
   ```bash
   ./gradlew installDebug
   ```

For more detailed setup instructions, see [DEVELOPMENT.md](DEVELOPMENT.md).

## Development Workflow

1. **Sync with upstream**:
   ```bash
   git checkout main
   git pull upstream main
   git push origin main
   ```

2. **Create a feature branch**:
   ```bash
   git checkout -b feature/your-feature-name
   # or
   git checkout -b fix/your-bug-fix
   ```

3. **Make your changes**:
   - Write clean, readable code
   - Follow the coding standards
   - Add comments where necessary
   - Update documentation if needed

4. **Test your changes**:
   - Test on multiple Android versions if possible
   - Test on different screen sizes
   - Ensure no regressions

5. **Commit your changes**:
   ```bash
   git add .
   git commit -m "Your commit message"
   ```

6. **Push to your fork**:
   ```bash
   git push origin feature/your-feature-name
   ```

7. **Create a Pull Request** on GitHub

## Coding Standards

### Kotlin Style

- Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use meaningful variable and function names
- Keep functions focused and small
- Prefer `val` over `var` when possible
- Use data classes for data structures

### Code Formatting

- Use 4 spaces for indentation (not tabs)
- Maximum line length: 120 characters
- Use trailing commas in multi-line lists
- Format code using Android Studio's auto-formatter (Ctrl+Alt+L / Cmd+Option+L)

### Android Best Practices

- Follow [Android Architecture Guidelines](https://developer.android.com/topic/architecture)
- Use `ViewModel` for UI-related data
- Handle lifecycle properly
- Request permissions at runtime
- Support dark mode and different screen sizes

### Example

```kotlin
// Good
private fun loadAppList() {
    val apps = packageManager.getInstalledPackages(0)
        .filter { isLaunchable(it) }
        .sortedBy { it.applicationInfo.loadLabel(packageManager).toString() }
    
    appAdapter.submitList(apps)
}

// Avoid
private fun loadAppList() {
    var apps = packageManager.getInstalledPackages(0)
    var filtered = mutableListOf<PackageInfo>()
    for (app in apps) {
        if (isLaunchable(app)) {
            filtered.add(app)
        }
    }
    // ... more complex code
}
```

## Commit Guidelines

### Commit Message Format

Use clear, descriptive commit messages:

```
type(scope): brief description

Optional longer explanation if needed
```

### Types

- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation changes
- `style`: Code style changes (formatting, etc.)
- `refactor`: Code refactoring
- `test`: Adding or updating tests
- `chore`: Maintenance tasks

### Examples

```
feat(search): add voice search functionality

fix(dock): resolve app icon not updating after uninstall

docs(readme): update setup instructions

refactor(settings): simplify preference management
```

## Pull Request Process

1. **Update your branch**:
   ```bash
   git checkout main
   git pull upstream main
   git checkout your-branch
   git rebase main
   ```

2. **Ensure your code**:
   - Follows coding standards
   - Is properly tested
   - Has no linter errors
   - Builds successfully

3. **Create Pull Request**:
   - Use a clear, descriptive title
   - Fill out the PR template
   - Reference related issues
   - Add screenshots for UI changes
   - Describe what changed and why

4. **Respond to feedback**:
   - Address review comments
   - Make requested changes
   - Update the PR as needed

5. **Wait for review**:
   - Maintainers will review your PR
   - Be patient and responsive
   - Be open to suggestions

### PR Checklist

Before submitting, ensure:

- [ ] Code follows the project's coding standards
- [ ] Code is tested and works as expected
- [ ] No linter errors or warnings
- [ ] Documentation is updated (if needed)
- [ ] Commit messages follow guidelines
- [ ] PR description is clear and complete
- [ ] Related issues are referenced

## Reporting Bugs

### Before Submitting

1. Check existing issues to avoid duplicates
2. Test with the latest version
3. Try to reproduce on a clean install

### Bug Report Template

When creating a bug report, include:

- **Description**: Clear description of the bug
- **Steps to Reproduce**: Detailed steps to reproduce
- **Expected Behavior**: What should happen
- **Actual Behavior**: What actually happens
- **Environment**:
  - Android version
  - Device model
  - App version
  - Screenshots (if applicable)
- **Logs**: Relevant logcat output (if applicable)

## Suggesting Features

### Before Suggesting

1. Check if the feature has been suggested
2. Consider if it aligns with the project's goals
3. Think about implementation complexity

### Feature Request Template

When suggesting a feature, include:

- **Problem**: What problem does this solve?
- **Solution**: Describe your proposed solution
- **Alternatives**: Other solutions you've considered
- **Additional Context**: Screenshots, mockups, examples

## Questions?

If you have questions about contributing:

- Open an issue with the `question` label
- Check existing documentation
- Review closed issues and PRs

Thank you for contributing to Launch! 🚀
