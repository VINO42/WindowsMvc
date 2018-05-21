package xyz.spacexplore.serverlet;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import xyz.spacexplore.annotation.Controller;
import xyz.spacexplore.annotation.Qualifier;
import xyz.spacexplore.annotation.RequestMapping;
import xyz.spacexplore.annotation.Service;
import xyz.spacexplore.controller.MvcController;

public class DispatcherServlet extends HttpServlet {
	private static final String BASE_PACKAGE_NAME = "xyz.spacexplore";
	// 包名路径列表
	private static final List<String> packagenames = new ArrayList<>();
	// 注解容器 key为注解的值,value为注解对应的对象
	private static final Map<String, Object> annotationsInstance = new HashMap<>();
	// methodHandlers容器双列集合 key 为 该请求的controller路径/requestMethod路径 value为
	// 具体的方法对象
	private static final Map<String, Object> methodHandlers = new HashMap<>();

	private static final long serialVersionUID = 1L;

	public DispatcherServlet() {
		super();
	}

	/**
	 * 初始init操作
	 */
	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		// 扫描包路径,获取包路径中的文件
		scanpackage(BASE_PACKAGE_NAME);
		// 获得每个类上的注解名和注解值
		try {
			filterAndBuildInstance();
		} catch (Exception e) {
			e.printStackTrace();
		}
		// 建立映射关系 即每个requestmapping对应的controller的方法的映射双列集合
		methodHandlerMapping();
		// ioc容器生成 注入元素属性
		IOCContainerBuild();

	}

	/**
	 * 构建ioc容器
	 */
	private void IOCContainerBuild() {
		if (annotationsInstance.size() == 0) {
			return;
		}
		for (Map.Entry<String, Object> entry : annotationsInstance.entrySet()) {
			Object value = entry.getValue();
			// 获取到有注解的每个类的字段集合
			Field[] fields = value.getClass().getDeclaredFields();
			if (fields != null && fields.length != 0) {
				for (Field field : fields) {
					// 暴力破解字段
					field.setAccessible(true);
					if (field.isAnnotationPresent(Qualifier.class)) {
						Qualifier qualifier = field.getAnnotation(Qualifier.class);
						String name = qualifier.value();
						try {
							// 这样就把字段元素的注解上的值和对应的类联系上
							field.set(value, annotationsInstance.get(name));
						} catch (IllegalArgumentException | IllegalAccessException e) {
							e.printStackTrace();
						}
					}
				}
			}
		}

	}

	/**
	 * 获得方法上requestMapping路径映射
	 */
	private void methodHandlerMapping() {
		if (annotationsInstance.size() == 0) {
			return;
		}
		// 遍历取到的
		for (Map.Entry<String, Object> entry : annotationsInstance.entrySet()) {
			Object obj = entry.getValue();
			if (obj.getClass().isAnnotationPresent(Controller.class)) {
				RequestMapping controller = entry.getValue().getClass().getAnnotation(RequestMapping.class);
				String value1 = controller.value();

				Method[] declaredMethods = entry.getValue().getClass().getMethods();
				for (Method method : declaredMethods) {
					if (method.isAnnotationPresent(RequestMapping.class)) {
						RequestMapping annotation = method.getAnnotation(RequestMapping.class);
						String name = annotation.value();
						// key为request路径的全路径 从controller 起
						methodHandlers.put("/" + value1 + "/" + name, method);
					} else {
						continue;
					}
				}
			} else {
				continue;
			}
		}

	}

	/**
	 * 获得所有带有mvc注解的实例
	 * 
	 * @throws ClassNotFoundException
	 */
	private void filterAndBuildInstance() throws Exception {
		if (packagenames.size() == 0) {
			return;
		}
		for (String classPath : packagenames) {
			String classPathName = classPath.replace(".class", "").trim();
			// 获得class对象
			Class<?> claz = Class.forName(classPathName);
			// 判断controller注解是否在该类上
			if (claz.isAnnotationPresent(Controller.class)) {
				// 获得实例
				Object newInstance = claz.newInstance();
				// 获得该类上的controller注解
				RequestMapping controller = newInstance.getClass().getAnnotation(RequestMapping.class);
				// 获得该注解上的value
				String value = controller.value();
				// 将controller的注解值作为key 将controller的实例对象作为value 存入map中
				annotationsInstance.put(value, newInstance);
			} else if (claz.isAnnotationPresent(Service.class)) {
				Object newInstance = claz.newInstance();
				Service service = newInstance.getClass().getAnnotation(Service.class);
				String key = service.value();
				annotationsInstance.put(key, newInstance);
			} else {
				// 其他注解不做处理 TODO 如果想处理其他自定义注解 可自己写
				continue;
			}
		}
	}

	private void scanpackage(String basePackageName) {
		URL url = this.getClass().getClassLoader().getResource("/" + replaceFileName(basePackageName));
		String fileUrl = url.getFile();
		File file = new File(fileUrl);
		String[] list = file.list();
		// 遍历拿到每个.class文件
		for (String filePath : list) {
			File file2 = new File(fileUrl + filePath);
			if (file2.isDirectory()) {
				// 接着递归遍历
				scanpackage(BASE_PACKAGE_NAME + "." + file2.getName());
			} else {
				// 将每个.class文件的全限定路径加入list中
				packagenames.add(basePackageName + "." + filePath);
			}

		}
	}

	/**
	 * 包名进行替换 把.替换为/
	 * 
	 * @param basePackageName
	 * @return
	 */
	private String replaceFileName(String basePackageName) {
		return basePackageName.replaceAll("\\.", "/");
	}

	@Override
	public void service(ServletRequest req, ServletResponse res) {
		HttpServletRequest httpreq = (HttpServletRequest) req;
		HttpServletResponse httpresp = (HttpServletResponse) res;
		String method = httpreq.getMethod();
		if (method.equals("GET")) {
			try {
				this.doGet(httpreq, httpresp);
			} catch (ServletException | IOException e) {
				e.printStackTrace();
			}
		}
		if (method.equals("POST")) {
			try {
				this.doPost(httpreq, httpresp);
			} catch (ServletException | IOException e) {
				e.printStackTrace();
			}
		}

	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		this.doPost(req, resp);
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String requestURI = req.getRequestURI();
		// 获得项目路径
		String contextPath = req.getContextPath();
		// 获得去掉项目名称及之前的 路径
		String path = requestURI.replace(contextPath, "");
		// 获得具体的方法
		Method method = (Method) methodHandlers.get(path);
		// 获得controller
		// /test/mapping
		MvcController controller = (MvcController) annotationsInstance.get(path.split("/")[1]);
		// 使用反射进行处理
		try {
			method.invoke(controller);
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			e.printStackTrace();
		}
	}

}
