fun main() {
    //Cfg.step1_compile = true
    NewAllStepsFunctions.func1Compile()

    //Cfg.step2_copyLibs = true
    NewAllStepsFunctions.func2CopyThirdLibs()

    //Cfg.step3_copyRes = true
    NewAllStepsFunctions.func3CopyRes()

    Cfg.step4_deps = true
    NewAllStepsFunctions.func4DepsCounter()
}
