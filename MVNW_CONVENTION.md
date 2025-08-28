# Maven Wrapper (mvnw) Convention

## 🎯 **Project Convention**

**Always use `mvnw` instead of `mvn`** in this project.

## ✅ **Correct Usage**

### **Building the Project**

```bash
# Build all modules
./mvnw clean package

# Build specific module
./mvnw -pl main-service clean package
./mvnw -pl zipper-service clean package
./mvnw -pl shared clean package
```

### **Running Tests**

```bash
# Run all tests
./mvnw test

# Run tests for specific module
./mvnw -pl main-service test
./mvnw -pl zipper-service test
```

### **Running Services**

```bash
# Main service
cd main-service
./mvnw spring-boot:run

# Zipper service
cd zipper-service
./mvnw spring-boot:run
```

### **Dependency Management**

```bash
# View dependency tree
./mvnw dependency:tree

# Download dependencies
./mvnw dependency:resolve
```

## ❌ **Incorrect Usage**

```bash
# Don't use these commands
mvn clean package
mvn test
mvn spring-boot:run
```

## 🔧 **Why Use mvnw?**

### **1. Version Consistency**

- Ensures all developers use the same Maven version
- Prevents "works on my machine" issues
- Guarantees reproducible builds

### **2. No Installation Required**

- Developers don't need to install Maven locally
- Works immediately after cloning the project
- Consistent across different environments

### **3. CI/CD Compatibility**

- Build servers automatically use the correct Maven version
- No need to configure Maven installation on CI/CD agents
- Ensures production builds match development builds

## 📁 **Maven Wrapper Files**

The project includes these Maven wrapper files:

- `mvnw` - Unix/Linux/macOS script
- `mvnw.cmd` - Windows script
- `.mvn/wrapper/maven-wrapper.properties` - Configuration
- `.mvn/wrapper/maven-wrapper.jar` - Wrapper JAR

## 🚀 **Setup and Usage**

### **Initial Setup**

```bash
# Make wrapper executable (Unix/Linux/macOS)
chmod +x mvnw

# Copy to modules if needed
cp mvnw main-service/
cp mvnw zipper-service/
chmod +x main-service/mvnw
chmod +x zipper-service/mvnw
```

### **Module Development**

```bash
# Each module has its own mvnw
cd main-service
./mvnw spring-boot:run

cd ../zipper-service
./mvnw spring-boot:run
```

### **Root Level Operations**

```bash
# Build all modules from root
./mvnw clean install

# Run tests for all modules
./mvnw test

# Package all modules
./mvnw package
```

## 🔍 **Troubleshooting**

### **Permission Denied**

```bash
# Make wrapper executable
chmod +x mvnw
chmod +x main-service/mvnw
chmod +x zipper-service/mvnw
```

### **Wrapper Not Found**

```bash
# Ensure mvnw exists in project root
ls -la mvnw

# Copy from root if missing in module
cp ../mvnw .
cp -r ../.mvn .
chmod +x mvnw
```

### **Version Issues**

```bash
# Check Maven wrapper version
./mvnw --version

# Update wrapper if needed (run from root)
./mvnw wrapper:wrapper
```

## 📋 **Best Practices**

1. **Always use `./mvnw`** instead of `mvn`
2. **Copy mvnw to modules** when creating new modules
3. **Make mvnw executable** in all locations
4. **Use relative paths** when running from modules
5. **Commit mvnw files** to version control

## 🎉 **Benefits**

- ✅ **Consistent builds** across all environments
- ✅ **No Maven installation** required
- ✅ **Version control** of Maven version
- ✅ **CI/CD compatibility** out of the box
- ✅ **Team productivity** improvements

## 🔗 **Related Documentation**

- [Maven Wrapper Official Documentation](https://maven.apache.org/wrapper/)
- [Multi-Module Architecture](MULTI_MODULE_ARCHITECTURE.md)
- [Development Setup](scripts/dev-setup.sh)

---

**Remember: Always use `mvnw` instead of `mvn` in this project!**
