var webpage = require("webpage").create();
webpage.open("http://www.google.pl").then(function(){
    window.console.log("Cos");
});
