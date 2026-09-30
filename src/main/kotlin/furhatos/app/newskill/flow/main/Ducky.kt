package furhatos.app.newskill.flow.main

import furhatos.app.newskill.donottouch.GeminiClient
import furhatos.app.newskill.flow.Parent
import furhatos.app.newskill.gemini.schemas.CaveManReply
import furhatos.app.newskill.nlu.CalmDown
import furhatos.flow.kotlin.State
import furhatos.flow.kotlin.furhat
import furhatos.flow.kotlin.onNoResponse
import furhatos.flow.kotlin.onResponse
import furhatos.flow.kotlin.state

val Ducky: State = state(Parent) {
    val gemini = GeminiClient()
    
    onEntry {
        // Listen to what the user has to say, or maybe even prompt the user?
        furhat.ask("What can Bork help you with?")
    }

    onResponse<CalmDown> {
        // Calm the robot down
        furhat.listen()
    }
    
    onNoResponse {
        val reply = gemini.generate(
            prompt = CaveManReply.prompt(""),
            format = CaveManReply.Format,
            systemInstruction = CaveManReply.systemInstructions
        )
        furhat.ask(reply.response)
    }
    
    onResponse {
        // placeholder
        println("user said: ${it.text}")
        val reply = gemini.generate(
            prompt = CaveManReply.prompt(it.text),
            format = CaveManReply.Format,
            systemInstruction = CaveManReply.systemInstructions,
            thinkingBudget = null,
        )
        furhat.ask(reply.response)
        // 1st assignment
        // 1. Make the persona more helpful, create a rubber ducky that helps with programming questions.
        // 2. Based on what programming language the question is in, change the face of the robot.

        // 2nd assignment
        // 1. Extend the response with gestures to make the robot
        //      feel more life like.

        // 3rd assignment
        // Coworkers can be annoying sometimes, so at some point we should become angry!
        // 1. Have a value increase by the absurdness of the user's response.
        // 2. Once we surpass a set threshold make the robot explode and outrage!
    }
}