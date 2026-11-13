horizon/launcher/Launcher.java:42: error: method run in class Launcher cannot be applied to given types;
            run(base, exe.toString(), initArgs.toArray(new String[0]));
            ^
  required: Path,String[]
  found:    Path,String,String[]
  reason: varargs mismatch; no instance(s) of type variable(s) T exist so that T[] conforms to String
  where T is a type-variable:
    T extends Object declared in method <T>toArray(T[])
1 error
javac exit=1
