$cp = (Get-Content target\cp-test.txt) + ";target\classes"
java -javaagent:"C:\Users\Admin\.m2\repository\org\projectlombok\lombok\1.18.38\lombok-1.18.38.jar" `
  -jar "C:\Users\Admin\.vscode\extensions\redhat.java-1.56.0-win32-x64\server\plugins\org.eclipse.jdt.core.compiler.batch_3.46.100.v20260826-1225.jar" `
  -21 -cp $cp -d none -warn:+unusedImport,+unusedLocal,+unusedPrivate,+deprecation,+resource `
  src/main/java src/test/java 2>&1 | Out-File -FilePath target\all_warnings_clean.txt -Width 1000 -Encoding utf8

