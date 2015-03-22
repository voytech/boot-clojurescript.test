
var testCljsTestPresence = function() {
	return (typeof cemerick !== "undefined" &&
		    typeof cemerick.cljs !== "undefined" &&
		    typeof cemerick.cljs.test !== "undefined" &&
		    typeof cemerick.cljs.test.run_all_tests === "function");
};
var execute = function(){
   if (testCljsTestPresence()){
      var results = cemerick.cljs.test.run_all_tests();
	  cemerick.cljs.test.on_testing_complete(results, function () {
	      window.alert(exitCodePrefix +
			 (cemerick.cljs.test.successful_QMARK_(results) ? 0 : 1));
	});
   }
}
 