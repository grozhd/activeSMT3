package custom;

import java.security.Permission;

public class SystemExitPrevention {
 
    public static void main(String[] args) throws Exception {
        System.out.println("Preventing System.exit");
        SystemExitControl.forbidSystemExitCall();
 
        try {
            System.out.println("Running a program which calls System.exit");
            main.main(args);
        } catch (SystemExitControl.ExitTrappedException e) {
            System.out.println("Forbidding call to System.exit");
        }
 
        System.out.println("Allowing System.exit");
        SystemExitControl.enableSystemExitCall();
 
    }
}
  
class SystemExitControl {
 
    public static class ExitTrappedException extends SecurityException {
    }
 
    public static void forbidSystemExitCall() {
        final SecurityManager securityManager = new SecurityManager() {
            @Override
            public void checkPermission(Permission permission) {
                if (permission.getName().contains("exitVM")) {
                    throw new ExitTrappedException();
                }
            }
        };
        System.setSecurityManager(securityManager);
    }
 
    public static void enableSystemExitCall() {
        System.setSecurityManager(null);
    }
}
