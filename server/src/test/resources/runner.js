// If Mocha is executed by Runtime.exec from Java, last a few logs are lost
// which tell the result of the test for some reason. This way seem to prevent
// the issue.
var Mocha = require("mocha");
var mocha = new Mocha();

mocha.addFile("./src/test/resources/node_modules/cettia-protocol/test/server.js");
mocha.run(function (failures) {
    process.on("exit", function () {
        process.exit(failures);
    });
});

