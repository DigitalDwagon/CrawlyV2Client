# Crawly Project: Client v2
This is the version 2 client for the Crawly Project. It takes in a list of URLs from the central tracker, crawls them, extracts metadata (such as outlinks, headers, and meta tags) and submits it all back in batches (to avoid overloading the server). It uses jSoup for all connection and HTML parsing responsibilities, and org.json for json sending and parsing.

This code is published so that others can see and use it as an example or starting point - it's not currently in a state where it can be run on it's own without tweaks and modifications. If you think you can improve this issue (such as by adding configurability) or otherwise help the project by submitting code, I welcome all contributions.

Current, known issues:
- There are some issues with the cookie jar
- Better error handling should be in place
- It would always be nice to support more file types (like parsing css/js for outlinks)
- The code is a bit messy, since it's all in one class
- There isn't a clean way to finish the current tasks and exit, it just exits.
