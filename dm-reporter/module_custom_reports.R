# Mandatory: list of custom reports
CUSTOM_REPORTS <- c('Dummy report')
CUSTOM_REPORTS_DESCR <- list('Dummy report' = 'My dummy report for dm-reporter testing')

# Mandatory: generic custom report function
getCustomReport <- function(urlBase, auth, custom_report) {
	
	if (custom_report == 'Dummy report') return(data.frame(a=c(1,2,3),b=c('a','b','c')))
	else stop(sprintf("The requested custom report (%s) is not supported.", custom_report))
	
}
