package furhatos.app.newskill.flow.main

import furhatos.app.newskill.flow.Parent
import furhatos.app.newskill.nlu.CalmDown
import furhatos.flow.kotlin.State
import furhatos.flow.kotlin.furhat
import furhatos.flow.kotlin.onResponse
import furhatos.flow.kotlin.state

val Ducky: State = state(Parent) {
    onEntry {
        // Listen to what the user has to say, or maybe even prompt the user?
        furhat.ask("What can I help you with?")
    }
    
    onResponse<CalmDown> {
        // Calm the robot down
    }
    
    onResponse {
        // 1st assignment
        // 1. Send what the user said to gemini 
        // 2. Fetch the response from gemini including the language code
        // 3. Utter the response in the language that the prompt was in.
        // 4. Update the language.
        
        // 2nd assignment
        // 1. Extend the response with gestures to make the robot
        //      feel more life like.
        
        // 3rd assignment
        // Coworkers can be annoying sometimes, so at some point we should become angry!
        // 1. Have a value increase by the absurdness of the user's response.
        // 2. Once we surpass a set threshold make the robot explode and outrage!
    }
}