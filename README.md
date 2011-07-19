## Based on:
    http://www.grails.org/plugin/ui-performance
## Originated from:
    http://jira.codehaus.org/browse/GRAILSPLUGINS-2568
## Original author:
Burt Beckwith


## Goodies:
- md5sum of all the content of webapp/js, webapp/css and webapp/images automatically. You change something it change the entire version
- recompiled smartsprites to work without conflicts with google-collections stable version
- you can ignore regex based resources from being minified with something like

    uiperformance.excludedMinifiedJs = [/.+jquery.+/, /.+robots.+/]

- new function in taglib to embebed css minified with embebedcss and uiperformance.embebedcss flag to on/off
- you can use another baseurl if you wan't to use CDN implementing something like:

    uiperformance.staticBaseUrlGenerator = {HttpServletRequest request ->
        return "http:////my.static.com"
    }

- You can serve older procesed static resources configuring a list of war path (files or urls, it may take a while downloading the urls). Something like:

    uiperformance.olderwars = ["http://mymaven/content/repositories/myrepo/myproyect/myapp/1.0.0/myapp-1.0.0.war"]

- also it serves 404 without cachecontrol, permits an unformatted url for background images in a css and it ill do your laundry.
