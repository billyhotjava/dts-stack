public class TestLoad {
    public static void main(String[] args) throws Exception {
        Class<?> clazz = Class.forName("com.yuzhi.dts.admin.web.rest.AdminApiResource");
        System.out.println("Loaded: " + clazz.getName());
        java.lang.reflect.Method[] methods = clazz.getDeclaredMethods();
        System.out.println("methods=" + methods.length);
    }
}
