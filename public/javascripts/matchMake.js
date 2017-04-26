var baseUrl = "http://hurtmeplenty.space"

var LoginBox = React.createClass({
    render: function() {
            return (
            <div id="login_box">
                    <p>In order to use this service you have to login via steam. </p>
                    <p>This website uses cookies to provide better user experience.
                    By logging in you accept cookies.</p>
                    <LoginButton/>
            </div>)
    }
})

var MatchMakeState = React.createClass({
    render: function() {
        return (<div id="mm_state" className="state_box">
                <div>{this.props.state}</div>
                <div><img src="assets/images/loader.gif"/></div>
                </div>)
    }
})

var MatchMakeFail = React.createClass({
    render: function() {
        return (<div id="mm_fail" className="state_box">Fail: {this.props.reason}</div>)
    }
})

var ConnectToServer = React.createClass({
    render: function() {
        // // notification
        // if (Notification.permission == "granted") {
        //     var msg = {body: "server is ready for you at " + $this.props.server,
        //                icon: "assets/images/qlico.png"}
        //     var n = new Notification('Server is ready', msg);
        //     setTimeout(n.close.bind(n), 5000);
        // }
        // // 
        setTimeout(function() {
            window.location.replace(this.props.server)
        }, 5000)
        return (
                <div id="connect_to_server" className="state_box">
                    <a href={this.props.server} className="server_link">{this.props.server}</a>
                </div>)
    }
})
 
var MatchMakeButton = React.createClass({
    render: function() {
        return (<button id="match_make_button" className="big_button" onClick={this.onClick}>Find match</button>)
    },
    onClick: function() {
        var socket = new WebSocket(wshost);
        this.props.onWsConnecting()
        socket.onopen = this.props.onWsOpen
        socket.onclose = this.props.onWsClosed
        socket.onmessage = this.props.onWsMessage
    }
})

var MatchMakeStartBox = React.createClass({
    getInitialState: function() {
        return {"phase": "idle"}
    },
    onWsConnecting: function() {
        this.setState({"phase": "connecting"})
    },
    onWsOpen: function() {
        this.setState({"phase": "connected"})
    },
    onWsClosed: function() {
        if (this.state.phase != "fail") {
            this.setState({"phase": "fail", "reason": "remote closed the connection"})
        }
    },
    onWsMessage: function(msgs) {
        if (msgs.type == 'open') {
            this.onWsOpen();
            return;
        }
        if (typeof msgs.data == 'undefined') return
        var msg = JSON.parse(msgs.data)
        switch (msg.cmd) {
        case "enqueued":
            this.setState({"phase": "enqueued"})
            break
        case "newChallenge":
            this.setState({"phase": "newChallenge", "server": msg.body.server})
            break
        case "noCompetition":
            this.setState({"phase": "fail", "reason": msg.reason})
            break
        }
    },
    render: function() {
        switch (this.state.phase) {
        case "idle":
            return (<MatchMakeButton onWsConnecting={this.onWsConnecting}
                    onWsOpen={this.onWsOpen} onWsClosed={this.onWsClosed} onWsMessage={this.onWsMessage}/>)
        case "connecting":
            return (<MatchMakeState state="connecting"/>)
        case "connected":
            return (<MatchMakeState state="awaiting server state"/>)
        case "enqueued":
            return (<MatchMakeState state="awaiting challenge"/>)
        case "newChallenge":
            return (<ConnectToServer server={this.state.server}/>)
        case "fail":
            return (<MatchMakeFail reason={this.state.reason}/>)
        } 
    }
})

var OnDemandStartButton = React.createClass({
    render: function() {
        return (<button id="on_demand_start_button" className="big_button" onClick={this.onClick}>Create server</button>)
    },
    onClick: function() {
        this.props.onRequested()
        $.ajax({
            url: baseUrl + "/requestServer",
            dataType: 'json',
            data: { "isPrivate": this.props.isPrivate},
            cache: false,
            success: function(data) {
                this.props.onSuccess(data)
            }.bind(this),
            error: function(xhr, status, err) {
                console.error("", status, err.toString());
                this.props.onError(err.toString())
            }.bind(this)
        });
    }
})

var OnDemandIsPrivate = React.createClass({
    render: function() {
        return (<div id="isPrivateDiv">
                <label>Create private:</label>
                <input id="isPrivateCheckbox"
                       name="isPrivate"
                       type="checkbox"
                       checked={this.props.isPrivate}
                       onChange={this.onChange}/>
                </div>)
    },
    onChange: function(event) {
        this.props.togglePrivacy()
    }
})

var OnDemandStartBox = React.createClass({
    getInitialState: function() {
        return {"phase": "idle", "isPrivate": false}
    },
    render: function() {
        switch (this.state.phase) {
        case "idle":
            return (<div>
                    <OnDemandIsPrivate togglePrivacy={this.togglePrivacy} checked={this.state.isPrivate}/>
                    <OnDemandStartButton
                    onRequested={this.onServerRequested}
                    onSuccess={this.onServerRequestSuccess}
                    onError={this.onServerRequestFailure}
                    isPrivate={this.state.isPrivate}
                    />
                   </div>)
        case "requested":
            return (<MatchMakeState state="awaiting server state"/>)
        case "created":
            return (<ConnectToServer server={this.state.server}/>)
        case "fail":
            return (<MatchMakeFail reason={this.state.reason}/>)
        }
    },
    togglePrivacy: function() {
        this.setState({ "phase": this.state.phase,
                        "isPrivate": !this.state.isPrivate})
    },
    onServerRequested: function() {
        this.setState({"phase": "requested", "isPrivate": this.state.isPrivate})
    },
    onServerRequestSuccess: function(msg) {
        this.setState({"phase": "created", "server": msg.server, "isPrivate": this.state.isPrivate})
    },
    onServerRequestFailure: function(reason) {
        this.setState({"phase": "fail", "reason": reason, "isPrivate": this.state.isPrivate})
    }
})

var MatchMakeBox = React.createClass({
    render: function() {
        if (mode == "matchmake") {
            return (<div>
                      <MatchMakeStartBox/>
                    </div>)
        } else {
            return (<div>
                      <OnDemandStartBox/>
                    </div>)
        }
    }
})

var Rules = React.createClass({
    render: function() {
        return (<div id="rules">
                <h3>Rules</h3>
                <ul id="rules_list">
                <li>Servers are deleted on inactivity, disconnects</li>
                <li>Duels only</li>
                <li>Glickos at the moment grabbed from <a href="qlstats.net">qlstats.net</a></li>
                </ul>
                </div>)
    }
})

var LoggedInBox = React.createClass({
    render: function() {
        // Notification.requestPermission() 
        return (
                <div id="logged_in_box">
                  <div id="welcome">
                    <div>You are logged in as </div>
                    <div id="welcome_avatar"><img src={this.props.userAvatar}/></div>
                    <div id="welcome_nick">{this.props.userName}</div>
                    <button id="logout_button" className="small_button" onClick={this.logout}>Logout</button> 
                  </div>
                  <Rules/>
                  <MatchMakeBox/>
                </div>)
    },
    logout: function() {
        window.location.replace(baseUrl + "/logout") 
    }
})

var LoginButton = React.createClass({
    render: function() {
            return (
                    <img src="assets/images/login_button.png" onClick={this.onClick}/>
            )
    },
    onClick: function() {
        window.location.replace(baseUrl + "/login") 
    }
})

var ContentBox = React.createClass({
    getInitialState: function() {
        return {"isLoggedIn": typeof userName != 'undefined'}
    },
    render: function() {
        if (this.state.isLoggedIn) {
            return (<div id="react_top_box"><LoggedInBox userName={userName} userAvatar={userAvatar}/></div>)
        } else {
            return (<div id="react_top_box"><LoginBox/></div>)
        }
    }
})

ReactDOM.render(
  <ContentBox/>,
  document.getElementById('content')
);
