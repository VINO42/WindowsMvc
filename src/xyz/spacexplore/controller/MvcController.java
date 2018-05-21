package xyz.spacexplore.controller;

import xyz.spacexplore.annotation.Controller;
import xyz.spacexplore.annotation.Qualifier;
import xyz.spacexplore.annotation.RequestMapping;
import xyz.spacexplore.service.AtomService;

@Controller
@RequestMapping("test")
public class MvcController {

	@Qualifier(value = "atomServiceImpl")
	private AtomService atomService;

	@RequestMapping("test")
	public void test() {
		System.out.println("请求成功 了!!!!!!!!!!!!!!!!");
	}
}
