<%-- 
This default stuff goes in the HTML head element of each page
You can override it with your own file via WEB-INF/tiles-def.xml
--%>
<%@ include file="/WEB-INF/jsps/taglibs-struts2.jsp" %>

<link rel="stylesheet" type="text/css" media="all" href="<s:url value='/roller-ui/yui/reset-fonts-grids/reset-fonts-grids.css'/>" />
<link rel="stylesheet" type="text/css" media="all" href="<s:url value='/roller-ui/yui/base/base-min.css'/>" />
<link rel="stylesheet" type="text/css" media="all" href='<s:url value="/roller-ui/yui/tabview/assets/skins/sam/tabview.css"></s:url>'/>

<link rel="stylesheet" type="text/css" media="all" href='<s:url value="/roller-ui/styles/layout.css"/>' />
<link rel="stylesheet" type="text/css" media="all" href='<s:url value="/roller-ui/styles/roller.css"/>' />
<link rel="stylesheet" type="text/css" media="all" href='<s:url value="/roller-ui/styles/menu.css"/>' />

<link rel="stylesheet" type="text/css" media="all" href="<%= request.getContextPath() %>/roller-ui/theme/<s:property value="getProp('editor.theme')"/>/colors.css" />
<link rel="stylesheet" type="text/css" media="all" href='<s:url value="/roller-ui/theme/sunny/jquery-ui-1.10.4.custom.min.css"/>' />

<script type="text/javascript" src="<s:url value="/theme/scripts/roller.js"/>"></script>

<%-- struts2 head disabled until we need it --%>
<%-- <s:head theme="ajax"/> --%>