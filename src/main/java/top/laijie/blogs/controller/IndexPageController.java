package top.laijie.blogs.controller;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;

import top.laijie.blogs.domain.Categories;
import top.laijie.blogs.domain.Comments;
import top.laijie.blogs.domain.Posts;
import top.laijie.blogs.domain.User;
import top.laijie.blogs.domain.dto.CommentsDto;
import top.laijie.blogs.keyword.SensitivewordFilter;
import top.laijie.blogs.service.CategorieService;
import top.laijie.blogs.service.CommentService;
import top.laijie.blogs.service.PostsService;
import top.laijie.blogs.service.impl.PostsServiceImpl;
import top.laijie.blogs.service.impl.UserServiceImpl;
import top.laijie.blogs.tool.DateUtils;
import top.laijie.blogs.tool.Page;
import top.laijie.blogs.tool.UserUtils;
/**
 * 匹配个人主页功能
 * @author laijie
 *
 */
@Controller
@RequestMapping("")
public class IndexPageController {
	 private static Logger logger = Logger.getLogger(IndexPageController.class.getName());  
	 
	 @Autowired  
	 private UserServiceImpl userService;   
	 
	 @Autowired  
	 private PostsService postService;
	 
	 @Autowired
	 private CategorieService categorieService;
	 
	 @Autowired
	 private CommentService commentService;
	 
	 //个人主页
	 @RequestMapping(value="/{name}", method = {RequestMethod.GET})
	public String getDetail(@PathVariable("name") String name,ModelMap map,HttpServletRequest request){
		Query query = new Query();  
        query.addCriteria(Criteria.where("blogaddress").is(name));  
	    User user = userService.findOne(query);
	    if(user!=null){
	 		//Query query2 = new Query();
	 		 Query query2 = new Query(Criteria.where("uid").is(user.get_id()));
	 		query2.with(new Sort(Sort.Direction.DESC, "postdate"));
	 		query2.addCriteria(Criteria.where("status").is(1)); 
	 		Query query3 = new Query(Criteria.where("visible").is(0));
	 		 query3.addCriteria(Criteria.where("uid").is(user.get_id()));
	 		 Page<Categories> catelist = categorieService.listCategories(1,100,query3);
	 		 map.addAttribute("cateList",catelist.getDatas());
	    	 map.addAttribute("user", user);
	    	 String old = DateUtils.timeDifference(new Date(), user.getRegisterTime());
	    	 map.put("old", old);
	 	    return "/front/index.jsp";
	    }else{
	    	map.addAttribute("message", "该页面不存在");
	    	return "/register/activate_failure.jsp";
	    }
	 
	}
	 //文章列表
	 @RequestMapping("/post/{name}")
	 public String postList(@PathVariable("name") String name,ModelMap map,HttpServletRequest request){
		 String cateId = request.getParameter("cateId");
		 String title = request.getParameter("title");
		 String pageNum = request.getParameter("pageNo");
			int pageNo = 1;
			if(StringUtils.isNotBlank(pageNum)){
				pageNo = Integer.parseInt(pageNum);
			}
			Query query = new Query();  
			query.addCriteria(Criteria.where("blogaddress").is(name));  
		    User user = userService.findOne(query);
	 		 Query query2 = new Query(Criteria.where("uid").is(user.get_id()));
	 		query2.with(new Sort(Sort.Direction.DESC, "postdate"));
	 		if(cateId!=null&&!cateId.equals("")){
	 			ObjectId categoryId = new ObjectId(cateId);
	 			query2.addCriteria(Criteria.where("categorieId").is(categoryId)); 
	 		}
	 		if(title!=null&&!title.equals("")){
	 			query2.addCriteria(new Criteria("title").regex(".*?"+title+".*")); 
	 		}
	 		query2.addCriteria(Criteria.where("status").is(1)); 
	 		Page<Posts> postList = postService.listPost(pageNo , query2);
	    	 map.addAttribute("name", name);
	    	 map.addAttribute("postPage", postList);
		 return "/front/post/post_list.jsp";
	 }
	 
	 //文章详情
	 @RequestMapping("*/p/detail.htm")
	 public String postDetail(String id,ModelMap map){
	 	//System.out.println(id);
		 ObjectId _id = new ObjectId(id);
	 	 postService.changereadNum(_id, true);
	 	 Posts post = postService.findByOBjId(_id);
	 	 map.put("post", post);
		 return "/front/post/post_detail.jsp";
	 }
	 
	 //评论列表
	 @RequestMapping("/comment/list")
	 public String commitList(String postId,ModelMap map){
		 String email = UserUtils.getCurrentLoginName();
	 	 if(email.equals("anonymousUser")){
	 		 map.put("nickname", "anonymousUser");
	 	 }else{
	 		User currentUser = userService.getUserByEmail(email);
	 		map.put("nickname", currentUser.getNicename());
	 	 }
	 	 
	 	Query query= new Query(Criteria.where("status").is(1));
	 	if(postId!=null&&!postId.equals("")){
			 ObjectId pid = new ObjectId(postId);
			 query.addCriteria(Criteria.where("postId").is(pid));
		 }
		Page<Comments> commentPage = commentService.listComment(1, query);
		List<CommentsDto> cdtolist = new ArrayList<>();
		for(Comments comment: commentPage.getDatas()){
			CommentsDto cdto = CommentsDto.CommentsToDto(comment);
			User user = userService.findByOBjId(comment.getUserId());
			cdto.setUser(user);
			cdtolist.add(cdto);
			};
		map.put("commentList",cdtolist);
		map.put("postId", postId);
		return "/front/comment/comment_detail.jsp";
	 }
	 //发表评论
	 @RequestMapping("/comment/create")
	 public String createCommit(String content,String postId,ModelMap map){
		 Comments comment = new Comments();
		 if(content!=null&&!content.equals("")){
			 comment.setContent(repalce(content));
		 }
		 if(postId!=null&&!postId.equals("")){
			 ObjectId pid = new ObjectId(postId);
			 comment.setPostId(pid);
		 }
		 comment.setCreateDate(new Date());
		 comment.setStatus(1);
		 String email = UserUtils.getCurrentLoginName();
	 	 User currentUser = userService.getUserByEmail(email);
	 	 comment.setUserId(currentUser.get_id());
		 commentService.createCommit(comment);
		 postService.changeCommentNum(comment.getPostId(), true);
		 return "redirect:/comment/list?postId="+postId;
	 };
	 @RequestMapping("/comment/tocreate")
	 public String tOcreateCommit(String postId){

		 return "redirect:/comment/list?postId="+postId;
	 };
	 private String repalce(String string){
		 SensitivewordFilter filter = new SensitivewordFilter();
			return filter.replaceSensitiveWord(string,2,"*");
	 }
}
